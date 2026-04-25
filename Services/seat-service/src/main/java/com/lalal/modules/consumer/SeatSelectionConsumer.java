package com.lalal.modules.consumer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lalal.modules.dao.TrainDO;
import com.lalal.modules.dao.TrainStationDO;
import com.lalal.modules.dto.SeatSelectionRequestMessage;
import com.lalal.modules.dto.SeatSelectionResultMessage;
import com.lalal.modules.dto.request.SeatSelectionRequestDTO;
import com.lalal.modules.dto.response.TicketDTO;
import com.lalal.modules.mapper.TrainMapper;
import com.lalal.modules.mapper.TrainStationMapper;
import com.lalal.modules.model.Passenger;
import com.lalal.modules.mq.MessageQueueService;
import com.lalal.modules.mq.annotation.MessageConsumer;
import com.lalal.modules.mq.rocketmq.RocketMQBaseConsumer;
import com.lalal.modules.service.SeatSelectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 座位选择消息消费者
 * 监听 seat-selection-topic，处理购票请求的座位选择
 *
 * <p>支持两种来源：
 * 1. NORMAL - 普通购票：无票直接失败
 * 2. WAITLIST - 候补订单：无票返回 null，触发继续排队
 *
 * 选座完成后发送 SeatSelectionResultMessage 到 seat-selection-result-topic
 *
 * @author Claude
 */
@Component
@Slf4j
@RequiredArgsConstructor
@MessageConsumer(
        topic = "seat-selection-topic",
        tag = "select",
        consumerGroup = "seat-selection-consumer"
)
@RocketMQMessageListener(
        topic = "seat-selection-topic",
        consumerGroup = "seat-selection-consumer",
        selectorExpression = "select"
)
public class SeatSelectionConsumer extends RocketMQBaseConsumer {

    private final SeatSelectionService seatSelectionService;
    private final MessageQueueService messageQueueService;
    private final TrainMapper trainMapper;
    private final TrainStationMapper trainStationMapper;

    private static final String SEAT_SELECTION_RESULT_TOPIC = "seat-selection-result-topic";

    // 候补订单座位锁定时间（分钟）- 比普通购票更短
    private static final int WAITLIST_SEAT_LOCK_MINUTES = 10;
    private static final int NORMAL_SEAT_LOCK_MINUTES = 30;

    @Override
    protected void doProcess(Object msg) {
        SeatSelectionRequestMessage message = (SeatSelectionRequestMessage) msg;
        String requestId = message.getRequestId();

        log.info("[座位选择] 收到消息: requestId={}, trainNum={}, source={}",
                requestId, message.getTrainNum(), message.getSource());

        try {
            // 构建 SeatSelectionRequestDTO
            SeatSelectionRequestDTO seatRequest = buildSeatSelectionRequestDTO(message);

            // 调用座位选择服务
            TicketDTO selectedSeats = seatSelectionService.select(seatRequest);

            // 判断来源，决定无票时的处理逻辑
            boolean isWaitlist = "WAITLIST".equals(message.getSource());

            if (selectedSeats == null || selectedSeats.getItems() == null || selectedSeats.getItems().isEmpty()) {
                if (isWaitlist) {
                    // 候补订单无票：返回 null，上层继续排队（不发送失败消息）
                    log.info("[座位选择] 候补订单无可用座位，继续排队: requestId={}", requestId);
                } else {
                    // 普通购票无票：直接失败
                    log.warn("[座位选择] 无可用座位: requestId={}", requestId);
                }
                sendFailureResult(requestId, message.getWaitlistSn(), "座位选择失败: 无可用座位");
                return;
            }

            // 发送成功结果
            SeatSelectionResultMessage resultMsg = new SeatSelectionResultMessage();
            resultMsg.setRequestId(requestId);
            resultMsg.setWaitlistSn(message.getWaitlistSn()); // 候补订单号
            resultMsg.setSuccess(true);
            resultMsg.setSelectedSeats(selectedSeats.getItems().stream()
                    .map(item -> {
                        SeatSelectionResultMessage.SeatItem seatItem = new SeatSelectionResultMessage.SeatItem();
                        seatItem.setPassengerId(item.getPassengerId());
                        seatItem.setCarriageNum(item.getCarriageNum());
                        seatItem.setSeatNum(item.getSeatNum());
                        seatItem.setSeatType(item.getSeatType());
                        return seatItem;
                    })
                    .collect(Collectors.toList()));
            resultMsg.setTimestamp(System.currentTimeMillis());

            // 填充计划发车/到达时间
            fillPlanTimes(resultMsg, message.getTrainNum(), message.getStartStation(),
                    message.getEndStation(), message.getDate());

            messageQueueService.send(SEAT_SELECTION_RESULT_TOPIC, "result", resultMsg);

            log.info("[座位选择] 处理成功: requestId={}, seatCount={}",
                    requestId, selectedSeats.getItems().size());

        } catch (Exception e) {
            log.error("[座位选择] 处理异常: requestId={}", requestId, e);
            sendFailureResult(requestId, message.getWaitlistSn(), "座位选择异常: " + e.getMessage());
            throw e; // 触发 MQ 重试
        }
    }

    private void sendFailureResult(String requestId, String waitlistSn, String errorMsg) {
        SeatSelectionResultMessage result = new SeatSelectionResultMessage();
        result.setRequestId(requestId);
        result.setWaitlistSn(waitlistSn);
        result.setSuccess(false);
        result.setErrorMessage(errorMsg);
        result.setTimestamp(System.currentTimeMillis());
        messageQueueService.send(SEAT_SELECTION_RESULT_TOPIC, "result", result);
    }

    private SeatSelectionRequestDTO buildSeatSelectionRequestDTO(SeatSelectionRequestMessage msg) {
        SeatSelectionRequestDTO dto = new SeatSelectionRequestDTO();
        dto.setAccount(StringUtils.hasText(msg.getAccount()) ? msg.getAccount() : "");
        dto.setTrainNum(msg.getTrainNum());
        dto.setStartStation(msg.getStartStation());
        dto.setEndStation(msg.getEndStation());
        dto.setDate(msg.getDate());

        List<Passenger> passengers = new ArrayList<>();
        for (int i = 0; i < msg.getPassengerIds().size(); i++) {
            Passenger p = new Passenger();
            p.setId(msg.getPassengerIds().get(i));
            p.setSeatType(msg.getSeatTypelist().get(i));
            if (msg.getChooseSeats() != null && i < msg.getChooseSeats().size()) {
                p.setSeatPreference(msg.getChooseSeats().get(i));
            }
            passengers.add(p);
        }
        dto.setPassengers(passengers);
        return dto;
    }

    /**
     * 从 t_train_station 查询出发站和到达站的时刻信息，填入消息
     */
    private void fillPlanTimes(SeatSelectionResultMessage resultMsg, String trainNum,
                               String startStation, String endStation, String dateStr) {
        try {
            // 获取列车信息
            TrainDO trainDO = trainMapper.selectOne(
                    new LambdaQueryWrapper<TrainDO>()
                            .eq(TrainDO::getTrainNumber, trainNum)
            );
            if (trainDO == null) {
                log.warn("[座位选择] 列车信息不存在，无法获取时刻: trainNum={}", trainNum);
                return;
            }

            LocalDate runDate = LocalDate.parse(dateStr);
            Long trainId = trainDO.getId();

            // 查询出发站
            LambdaQueryWrapper<TrainStationDO> departWrapper = new LambdaQueryWrapper<>();
            departWrapper.eq(TrainStationDO::getTrainId, trainId)
                    .eq(TrainStationDO::getStationName, startStation)
                    .select(TrainStationDO::getDepartureTime, TrainStationDO::getArriveDayDiff);
            TrainStationDO departStation = trainStationMapper.selectOne(departWrapper);

            // 查询到达站
            LambdaQueryWrapper<TrainStationDO> arriveWrapper = new LambdaQueryWrapper<>();
            arriveWrapper.eq(TrainStationDO::getTrainId, trainId)
                    .eq(TrainStationDO::getStationName, endStation)
                    .select(TrainStationDO::getArrivalTime, TrainStationDO::getArriveDayDiff);
            TrainStationDO arriveStation = trainStationMapper.selectOne(arriveWrapper);

            if (departStation != null && departStation.getDepartureTime() != null) {
                int departDayDiff = departStation.getArriveDayDiff() != null ? departStation.getArriveDayDiff() : 0;
                LocalDate departDate = runDate.plusDays(departDayDiff);
                resultMsg.setPlanDepartTime(departDate.atTime(departStation.getDepartureTime())
                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
            }

            if (arriveStation != null && arriveStation.getArrivalTime() != null) {
                int arriveDayDiff = arriveStation.getArriveDayDiff() != null ? arriveStation.getArriveDayDiff() : 0;
                LocalDate arriveDate = runDate.plusDays(arriveDayDiff);
                resultMsg.setPlanArrivalTime(arriveDate.atTime(arriveStation.getArrivalTime())
                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
            }

            log.debug("[座位选择] 时刻信息: trainNum={}, departTime={}, arriveTime={}",
                    trainNum, resultMsg.getPlanDepartTime(), resultMsg.getPlanArrivalTime());
        } catch (Exception e) {
            log.warn("[座位选择] 获取时刻信息失败: trainNum={}", trainNum, e);
        }
    }
}

