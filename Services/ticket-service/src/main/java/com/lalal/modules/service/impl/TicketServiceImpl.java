package com.lalal.modules.service.impl;

import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.core.type.TypeReference;
import com.lalal.framework.cache.SafeCacheTemplate;
import com.lalal.modules.context.RequestContext;
import com.lalal.modules.dto.*;
import com.lalal.modules.dto.request.PurchaseTicketRequestDto;
import com.lalal.modules.dto.response.AsyncTicketCheckVO;
import com.lalal.modules.dto.response.PurchaseTicketVO;
import com.lalal.modules.entity.TicketAsyncRequestDO;
import com.lalal.modules.entity.TrainDO;
import com.lalal.modules.enumType.RequestStatus;
import com.lalal.modules.enumType.ReturnCode;
import com.lalal.modules.mapper.TicketAsyncRequestMapper;
import com.lalal.modules.mapper.TrainMapper;
import com.lalal.modules.remote.OrderServiceClient;
import com.lalal.modules.remote.SeatServiceClient;
import com.lalal.modules.remote.UserServiceClient;
import com.lalal.modules.result.Result;
import com.lalal.modules.service.FareCalculationService;
import com.lalal.modules.service.TicketService;
import com.lalal.modules.mq.MessageQueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 购票服务实现 - 纯 MQ 消息驱动架构
 *
 * 购票流程:
 * 1. purchase() 发送 SeatSelectionRequestMessage 到 seat-selection-topic
 * 2. SeatSelectionConsumer (seat-service) 处理选座，发送结果到 seat-selection-result-topic
 * 3. SeatResultProcessorConsumer (ticket-service) 计算票价，发送到 order-creation-topic
 * 4. OrderCreationConsumer (order-service) 创建订单，发送结果到 order-creation-result-topic
 * 5. OrderResultProcessorConsumer (ticket-service) 更新最终状态
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TicketServiceImpl implements TicketService {

    private final UserServiceClient userServiceClient;
    private final SafeCacheTemplate safeCacheTemplate;
    private final MessageQueueService messageQueueService;
    private final SeatServiceClient seatServiceClient;
    private final TrainMapper trainMapper;
    private final FareCalculationService fareCalculationService;
    private  final  OrderServiceClient orderServiceClient;

    private static final String PEAK_STATUS_KEY = "traffic:peak:status";
    private static final String ASYNC_REQUEST_PREFIX = "ticket:async:req:";
    private static final String SEAT_SELECTION_TOPIC = "seat-selection-topic";

    /**
     * 状态常量
     */
    private static final int STATUS_PROCESSING = 0;      // 处理中
    private static final int STATUS_SUCCESS = 1;         // 成功
    private static final int STATUS_FAILED = 2;          // 失败
    private static final int STATUS_SEAT_SELECTED = 4;   // 选座成功，订单创建中
    private static final int STATUS_ORDER_CREATING = 5;  // 订单创建中

    @Override
    public PurchaseTicketVO purchase(PurchaseTicketRequestDto purchaseTicketRequestDto, Long userId) {
        // 基础校验
        if (userId == null) {
            return PurchaseTicketVO.failed("用户ID为空");
        }
        if (purchaseTicketRequestDto.getIDCardCodelist() == null
                || purchaseTicketRequestDto.getSeatTypelist() == null
                || purchaseTicketRequestDto.getIDCardCodelist().size() != purchaseTicketRequestDto.getSeatTypelist().size()) {
            return PurchaseTicketVO.failed("乘车人ID列表与座位类型列表不匹配");
        }

        // 检测是否为高峰时段
        String peakStatus = safeCacheTemplate.get(PEAK_STATUS_KEY, new TypeReference<String>() {});
        boolean peakHour = "true".equals(peakStatus);

        if (peakHour) {
            // 高峰模式：异步处理
            return handleAsyncPurchase(purchaseTicketRequestDto, userId);
        }

        // 低峰模式：同步处理
        return processCorePurchase(userId, purchaseTicketRequestDto.getTrainNum(),
                purchaseTicketRequestDto.getStartStation(), purchaseTicketRequestDto.getEndStation(),
                purchaseTicketRequestDto.getDate(), purchaseTicketRequestDto.getIDCardCodelist(),
                purchaseTicketRequestDto.getSeatTypelist(), purchaseTicketRequestDto.getChooseSeats(),
                purchaseTicketRequestDto.getAccount());
    }

    /**
     * 异步购票处理：发送选座请求消息
     */
    private PurchaseTicketVO handleAsyncPurchase(PurchaseTicketRequestDto request, Long userId) {
        String requestId = RequestContext.getRequestId();
        String asyncKey = ASYNC_REQUEST_PREFIX + requestId;

        // 1. 构建数据库记录
        TicketAsyncRequestDO record = TicketAsyncRequestDO.builder()
                .requestId(requestId)
                .userId(userId)
                .trainNum(request.getTrainNum())
                .date(request.getDate())
                .status(STATUS_PROCESSING)
                .account(StringUtils.hasText(request.getAccount()) ? request.getAccount() : "")
                .startStation(request.getStartStation())
                .endStation(request.getEndStation())
                .passengerIdsJson(JSON.toJSONString(request.getIDCardCodelist()))
                .seatTypelistJson(JSON.toJSONString(request.getSeatTypelist()))
                .chooseSeatsJson(request.getChooseSeats() != null ? JSON.toJSONString(request.getChooseSeats()) : null)
                .source("NORMAL")  // 普通购票
                .build();

        boolean set = safeCacheTemplate.setIfAbsent(asyncKey, record, 30, TimeUnit.MINUTES);
        if (!set) {
            return PurchaseTicketVO.failed("请求处理中，请稍后查询");
        }

        // 3. 缓存初始状态
        safeCacheTemplate.safeSet(asyncKey, record, 30, TimeUnit.MINUTES);

        // 4. 构建选座请求消息
        SeatSelectionRequestMessage message = new SeatSelectionRequestMessage();
        message.setRequestId(requestId);
        message.setUserId(userId);
        message.setAccount(record.getAccount());
        message.setTrainNum(request.getTrainNum());
        message.setStartStation(request.getStartStation());
        message.setEndStation(request.getEndStation());
        message.setDate(request.getDate());
        message.setPassengerIds(request.getIDCardCodelist());
        message.setSeatTypelist(request.getSeatTypelist());
        message.setChooseSeats(request.getChooseSeats());
        message.setTimestamp(System.currentTimeMillis());

        // 5. 发送消息
        try {
            messageQueueService.send(SEAT_SELECTION_TOPIC, "select", message);
            log.info("[购票] 发送选座请求消息: requestId={}, trainNum={}", requestId, request.getTrainNum());
        } catch (Exception e) {
            // 发送失败，更新状态
            record.setStatus(STATUS_FAILED);
            record.setErrorMessage("消息发送失败: " + e.getMessage());
            safeCacheTemplate.safeSet(asyncKey, record, 30, TimeUnit.MINUTES);
            log.error("[购票] 发送消息失败: requestId={}", requestId, e);
            return PurchaseTicketVO.failed("消息发送失败，请重试");
        }

        return PurchaseTicketVO.processing(requestId);
    }

    /**
     * 核心购票逻辑（内部重载，支持传入乘客列表）
     */
    @Override
    public PurchaseTicketVO processCorePurchase(Long userId, String trainNum, String startStation,
                                                 String endStation, String date, List<Long> passengerIds,
                                                 List<String> seatTypelist, List<String> chooseSeats,
                                                 String account) {
        // 查询乘客信息进行校验
        UserServiceClient.PassengersBatchRequest batchReq = new UserServiceClient.PassengersBatchRequest();
        batchReq.setUserId(userId);
        batchReq.setPassengerIds(passengerIds);
        Result<List<UserServiceClient.PassengerRemoteVO>> passengerResult = userServiceClient.batchPassengers(batchReq);
        if (passengerResult == null
                || passengerResult.getCode() == null
                || !ReturnCode.success.code().equals(passengerResult.getCode())
                || passengerResult.getData() == null
                || passengerResult.getData().isEmpty()) {
            return PurchaseTicketVO.failed("获取乘车人信息失败");
        }
        List<UserServiceClient.PassengerRemoteVO> passengers = passengerResult.getData();

        // 1. 选座
        SeatSelectionRequestDTO seatRequest = new SeatSelectionRequestDTO();
        seatRequest.setTrainNum(trainNum);
        seatRequest.setStartStation(startStation);
        seatRequest.setEndStation(endStation);
        seatRequest.setDate(LocalDate.parse(date));
        seatRequest.setAccount(StringUtils.hasText(account) ? account : "");

        List<SeatSelectionRequestDTO.PassengerDTO> seatPassengers = new ArrayList<>();
        for (int i = 0; i < passengers.size(); i++) {
            SeatSelectionRequestDTO.PassengerDTO p = new SeatSelectionRequestDTO.PassengerDTO();
            p.setId(passengers.get(i).getId());
            p.setSeatType(seatTypelist.get(i));
            if (chooseSeats != null && i < chooseSeats.size()) {
                p.setSeatPreference(chooseSeats.get(i));
            }
            seatPassengers.add(p);
        }
        seatRequest.setPassengers(seatPassengers);

        TicketDTO selectedSeats = seatServiceClient.select(seatRequest);
        if (selectedSeats == null || selectedSeats.getItems() == null) {
            return PurchaseTicketVO.failed("座位选择失败");
        }

        // 2. 获取列车信息
        TrainDO trainDO = trainMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TrainDO>()
                        .eq(TrainDO::getTrainNumber, trainNum)
        );

        // 3. 计算票价
        List<FareCalculationRequestDTO> fareRequests = new ArrayList<>();
        for (TicketDTO.TicketItem item : selectedSeats.getItems()) {
            FareCalculationRequestDTO fareRequest = new FareCalculationRequestDTO();
            fareRequest.setTrainNumber(trainNum);
            fareRequest.setDepartureStation(startStation);
            fareRequest.setArrivalStation(endStation);
            fareRequest.setSeatType(item.getSeatType());
            fareRequest.setPassengerType(0);
            fareRequest.setTrainBrand(trainDO != null ? trainDO.getTrainBrand() : null);
            fareRequest.setIsPeakSeason(false);
            fareRequest.setPassengerId(item.getPassengerId());
            if (trainDO != null) {
                fareRequest.setTrainId(trainDO.getId());
            }
            fareRequests.add(fareRequest);
        }

        List<FareCalculationResultDTO> fareResults = fareCalculationService.batchCalculateFare(fareRequests);

        // 4. 创建订单
        OrderServiceClient.OrderCreateRemoteRequestDTO orderRequest = new OrderServiceClient.OrderCreateRemoteRequestDTO();
        orderRequest.setTrainNumber(trainNum);
        orderRequest.setStartStation(startStation);
        orderRequest.setEndStation(endStation);
        orderRequest.setUsername(account);
        orderRequest.setRunDate(LocalDate.parse(date));

        List<OrderServiceClient.OrderCreateRemoteRequestDTO.OrderItemRemoteRequestDTO> orderItems = new ArrayList<>();
        for (int i = 0; i < selectedSeats.getItems().size(); i++) {
            TicketDTO.TicketItem item = selectedSeats.getItems().get(i);
            FareCalculationResultDTO fareResult = fareResults.get(i);

            OrderServiceClient.OrderCreateRemoteRequestDTO.OrderItemRemoteRequestDTO orderItem =
                    new OrderServiceClient.OrderCreateRemoteRequestDTO.OrderItemRemoteRequestDTO();
            orderItem.setPassengerId(item.getPassengerId());
            orderItem.setCarriageNumber(item.getCarriageNum());
            orderItem.setSeatNumber(item.getSeatNum());
            orderItem.setSeatType(item.getSeatType());
            orderItem.setAmount(fareResult.getTotalFare());

            UserServiceClient.PassengerRemoteVO matched = passengers.stream()
                    .filter(pv -> pv.getId().equals(item.getPassengerId()))
                    .findFirst()
                    .orElse(null);
            if (matched != null) {
                orderItem.setIdCard(matched.getIdCardNumber());
                orderItem.setRealName(matched.getRealName());
            } else {
                orderItem.setIdCard("");
                orderItem.setRealName("");
            }
            orderItems.add(orderItem);
        }
        orderRequest.setItems(orderItems);

        BigDecimal totalAmount = orderItems.stream()
                .map(OrderServiceClient.OrderCreateRemoteRequestDTO.OrderItemRemoteRequestDTO::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String orderSn = orderServiceClient.create(orderRequest);

        // 组装返回结果
        PurchaseTicketVO vo = new PurchaseTicketVO();
        vo.setStatus(RequestStatus.SUCCESS.toString());
        vo.setOrderSn(orderSn);
        vo.setTotalAmount(totalAmount);
        vo.setTicketDTO(selectedSeats);
        return vo;
    }

    @Override
    public AsyncTicketCheckVO check(String requestId, Long userId) {
        // 优先从缓存读取
        String asyncKey = ASYNC_REQUEST_PREFIX + requestId;
        TicketAsyncRequestDO record = safeCacheTemplate.get(asyncKey, new TypeReference<TicketAsyncRequestDO>() {});


        if (record == null) {
            return AsyncTicketCheckVO.failed(requestId, "请求不存在或已过期");
        }

        // 安全检查：确保用户只能查询自己的请求
        if (!record.getUserId().equals(userId)) {
            return AsyncTicketCheckVO.failed(requestId, "无权查询此请求");
        }

        switch (record.getStatus()) {
            case STATUS_PROCESSING:
                return AsyncTicketCheckVO.processing(requestId);
            case STATUS_SUCCESS:
                return AsyncTicketCheckVO.success(record.getRequestId(), record.getOrderSn(),
                        null, null);
            case STATUS_FAILED:
                return AsyncTicketCheckVO.failed(requestId, record.getErrorMessage());
            case STATUS_SEAT_SELECTED:
            case STATUS_ORDER_CREATING:
                // 中间状态，返回处理中
                return AsyncTicketCheckVO.processing(requestId);
            default:
                return AsyncTicketCheckVO.failed(requestId, "未知状态");
        }
    }

}
