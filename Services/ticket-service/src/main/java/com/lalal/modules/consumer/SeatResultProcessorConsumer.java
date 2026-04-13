package com.lalal.modules.consumer;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.lalal.framework.cache.SafeCacheTemplate;
import com.lalal.modules.dto.*;
import com.lalal.modules.entity.TicketAsyncRequestDO;
import com.lalal.modules.entity.TrainDO;
import com.lalal.modules.mapper.TicketAsyncRequestMapper;
import com.lalal.modules.mapper.TrainMapper;
import com.lalal.modules.mq.MessageQueueService;
import com.lalal.modules.mq.annotation.MessageConsumer;
import com.lalal.modules.mq.rocketmq.RocketMQBaseConsumer;
import com.lalal.modules.remote.UserServiceClient;
import com.lalal.modules.result.Result;
import com.lalal.modules.service.FareCalculationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 选座结果处理消费者
 * 监听 seat-selection-result-topic
 * 选座成功：计算票价 -> 发送订单创建请求
 * 选座失败：更新缓存状态为失败
 */
@Component
@Slf4j
@RequiredArgsConstructor
@MessageConsumer(
    topic = "seat-selection-result-topic",
    tag = "*",
    consumerGroup = "seat-result-processor-consumer"
)
@RocketMQMessageListener(
    topic = "seat-selection-result-topic",
    consumerGroup = "seat-result-processor-consumer",
    selectorExpression = "*"
)
public class SeatResultProcessorConsumer extends RocketMQBaseConsumer {

    private final SafeCacheTemplate safeCacheTemplate;
    private final MessageQueueService messageQueueService;
    private final TrainMapper trainMapper;
    private final FareCalculationService fareCalculationService;
    private final UserServiceClient userServiceClient;

    private static final String ASYNC_REQUEST_PREFIX = "ticket:async:req:";
    private static final String ORDER_CREATION_TOPIC = "order-creation-topic";

    @Override
    protected void doProcess(Object msg) {
        SeatSelectionResultMessage seatResult = (SeatSelectionResultMessage) msg;
        String requestId = seatResult.getRequestId();

        log.info("[选座结果处理] 收到消息: requestId={}, success={}", requestId, seatResult.isSuccess());

        // 查询异步请求记录
        String asyncKey = ASYNC_REQUEST_PREFIX + requestId;
        TicketAsyncRequestDO record = safeCacheTemplate.get(asyncKey, new TypeReference<TicketAsyncRequestDO>() {});

        if (record == null) {
            log.warn("[选座结果处理] 未找到请求记录: requestId={}", requestId);
            return;
        }

//        // 幂等性检查：如果状态已经是选座成功后的状态，跳过
//        if (record.getStatus() >= 5) { // 5 = ORDER_CREATING
//            log.info("[选座结果处理] 请求已处理后续阶段: requestId={}, status={}", requestId, record.getStatus());
//            return;
//        }

        if (!seatResult.isSuccess()) {
            // 选座失败，更新状态为失败
            record.setStatus(2); // FAILED
            record.setErrorMessage(seatResult.getErrorMessage());
            safeCacheTemplate.safeSet(asyncKey,record,30,TimeUnit.MINUTES);
            log.info("[选座结果处理] 选座失败: requestId={}, error={}", requestId, seatResult.getErrorMessage());
            return;
        }

        // 选座成功，继续处理：计算票价 -> 发送订单创建请求
        try {
            record.setStatus(5); // ORDER_CREATING
            safeCacheTemplate.safeSet(asyncKey,record,30,TimeUnit.MINUTES);

            // 获取乘客信息
            List<Long> passengerIds = JSON.parseArray(record.getPassengerIdsJson(), Long.class);
            UserServiceClient.PassengersBatchRequest batchReq = new UserServiceClient.PassengersBatchRequest();
            batchReq.setUserId(record.getUserId());
            batchReq.setPassengerIds(passengerIds);
            Result<List<UserServiceClient.PassengerRemoteVO>> passengerResult = userServiceClient.batchPassengers(batchReq);

            if (passengerResult == null || passengerResult.getData() == null || passengerResult.getData().isEmpty()) {
                log.error("[选座结果处理] 获取乘客信息失败: requestId={}", requestId);
                record.setStatus(2);
                record.setErrorMessage("获取乘客信息失败");
                safeCacheTemplate.safeSet(asyncKey,record,30,TimeUnit.MINUTES);
                return;
            }
            List<UserServiceClient.PassengerRemoteVO> passengers = passengerResult.getData();

            // 构建订单创建请求消息
            OrderCreationRequestMessage orderRequest = buildOrderCreationRequest(
                requestId, seatResult, record, passengers);

            // 发送订单创建消息
            messageQueueService.send(ORDER_CREATION_TOPIC, "create", orderRequest);

            log.info("[选座结果处理] 已发送订单创建请求: requestId={}", requestId);

        } catch (Exception e) {
            log.error("[选座结果处理] 构建订单请求异常: requestId={}", requestId, e);
            record.setStatus(2); // FAILED
            record.setErrorMessage("构建订单请求失败: " + e.getMessage());
            safeCacheTemplate.safeSet(asyncKey,record,30,TimeUnit.MINUTES);
            throw e;
        }
    }

    private OrderCreationRequestMessage buildOrderCreationRequest(
            String requestId, SeatSelectionResultMessage seatResult,
            TicketAsyncRequestDO requestRecord,
            List<UserServiceClient.PassengerRemoteVO> passengers) {

        OrderCreationRequestMessage orderMsg = new OrderCreationRequestMessage();
        orderMsg.setRequestId(requestId);
        orderMsg.setTrainNum(requestRecord.getTrainNum());
        orderMsg.setStartStation(requestRecord.getStartStation());
        orderMsg.setEndStation(requestRecord.getEndStation());
        orderMsg.setUsername(requestRecord.getAccount());
        orderMsg.setRunDate(requestRecord.getDate());

        // 获取列车信息
        TrainDO trainDO = trainMapper.selectOne(
            new LambdaQueryWrapper<TrainDO>()
                .eq(TrainDO::getTrainNumber, requestRecord.getTrainNum())
        );

        // 从座位结果构建订单项
        List<OrderCreationRequestMessage.OrderItem> items = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (SeatSelectionResultMessage.SeatItem seat : seatResult.getSelectedSeats()) {
            // 计算票价
            FareCalculationRequestDTO fareReq = new FareCalculationRequestDTO();
            fareReq.setTrainNumber(requestRecord.getTrainNum());
            fareReq.setDepartureStation(requestRecord.getStartStation());
            fareReq.setArrivalStation(requestRecord.getEndStation());
            fareReq.setSeatType(seat.getSeatType());
            fareReq.setPassengerType(0);
            fareReq.setTrainBrand(trainDO != null ? trainDO.getTrainBrand() : null);
            fareReq.setIsPeakSeason(false);
            fareReq.setPassengerId(seat.getPassengerId());
            if (trainDO != null) {
                fareReq.setTrainId(trainDO.getId());
            }

            List<FareCalculationResultDTO> fareResults = fareCalculationService.batchCalculateFare(List.of(fareReq));
            BigDecimal fare = fareResults.isEmpty() ? BigDecimal.ZERO : fareResults.get(0).getTotalFare();

            OrderCreationRequestMessage.OrderItem item = new OrderCreationRequestMessage.OrderItem();
            item.setPassengerId(seat.getPassengerId());
            item.setCarriageNumber(seat.getCarriageNum());
            item.setSeatNumber(seat.getSeatNum());
            item.setSeatType(seat.getSeatType());
            item.setAmount(fare);

            // 查找乘客信息填充真实姓名和身份证号
            UserServiceClient.PassengerRemoteVO matched = passengers.stream()
                .filter(p -> p.getId().equals(seat.getPassengerId()))
                .findFirst()
                .orElse(null);
            if (matched != null) {
                item.setRealName(matched.getRealName());
                item.setIdCard(matched.getIdCardNumber());
            } else {
                item.setRealName("");
                item.setIdCard("");
            }

            items.add(item);
            totalAmount = totalAmount.add(fare);
        }

        orderMsg.setItems(items);
        return orderMsg;
    }
}
