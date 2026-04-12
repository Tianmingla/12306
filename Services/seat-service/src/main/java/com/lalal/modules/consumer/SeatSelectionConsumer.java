package com.lalal.modules.consumer;

import com.lalal.modules.dto.SeatSelectionRequestMessage;
import com.lalal.modules.dto.SeatSelectionResultMessage;
import com.lalal.modules.dto.request.SeatSelectionRequestDTO;
import com.lalal.modules.dto.response.TicketDTO;
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
                    // 注意：这里返回 null 不会触发下面的发送逻辑，因为 selectedSeats 为 null
                    // 上层 WaitlistCheckConsumer 会收到 null 并处理
                    return;
                } else {
                    // 普通购票无票：直接失败
                    log.warn("[座位选择] 无可用座位: requestId={}", requestId);
                    sendFailureResult(requestId, "座位选择失败: 无可用座位");
                    return;
                }
            }

            // 发送成功结果
            SeatSelectionResultMessage resultMsg = new SeatSelectionResultMessage();
            resultMsg.setRequestId(requestId);
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

            messageQueueService.send(SEAT_SELECTION_RESULT_TOPIC, "result", resultMsg);

            log.info("[座位选择] 处理成功: requestId={}, seatCount={}",
                    requestId, selectedSeats.getItems().size());

        } catch (Exception e) {
            log.error("[座位选择] 处理异常: requestId={}", requestId, e);
            sendFailureResult(requestId, "座位选择异常: " + e.getMessage());
            throw e; // 触发 MQ 重试
        }
    }

    private void sendFailureResult(String requestId, String errorMsg) {
        SeatSelectionResultMessage result = new SeatSelectionResultMessage();
        result.setRequestId(requestId);
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
}

