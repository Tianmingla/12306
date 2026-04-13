package com.lalal.modules.consumer;

import com.lalal.modules.dto.OrderCreationRequestMessage;
import com.lalal.modules.dto.SeatSelectionResultMessage;
import com.lalal.modules.entity.WaitlistOrderDO;
import com.lalal.modules.service.PriorityCalculator;
import com.lalal.modules.service.WaitlistQueueService;
import com.lalal.modules.service.WaitlistService;
import com.lalal.modules.mq.MessageQueueService;
import com.lalal.modules.mq.annotation.MessageConsumer;
import com.lalal.modules.mq.rocketmq.RocketMQBaseConsumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.DateFormat;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

/**
 * 候补选座结果消费者
 *
 * <p>监听 seat-selection-result-topic，处理候补订单的选座结果。
 *
 * <p>处理逻辑：
 * 1. 选座成功 → 构建订单创建请求 → 发送 order-creation-topic
 * 2. 选座失败 → 回滚状态 → 重新入队(优先级降低)
 */
@Component
@Slf4j
@RequiredArgsConstructor
@MessageConsumer(
        topic = "seat-selection-result-topic",
        tag = "*",
        consumerGroup = "waitlist-seat-result-consumer"
)
@RocketMQMessageListener(
        topic = "seat-selection-result-topic",
        consumerGroup = "waitlist-seat-result-consumer",
        selectorExpression = "*"
)
public class WaitlistSeatResultConsumer extends RocketMQBaseConsumer {

    private final WaitlistService waitlistService;
    private final WaitlistQueueService waitlistQueueService;
    private final MessageQueueService messageQueueService;
    private final PriorityCalculator priorityCalculator;

    private static final String ORDER_CREATION_TOPIC = "order-creation-topic";
    private static final BigDecimal FAILURE_PENALTY = BigDecimal.valueOf(10);

    @Override
    protected void doProcess(Object msg) {
        SeatSelectionResultMessage message= (SeatSelectionResultMessage) msg;
        String requestId = message.getRequestId();
        String waitlistSn = message.getWaitlistSn();

        // 只处理候补订单的选座结果
        if (waitlistSn == null || waitlistSn.isBlank()) {
            return;
        }

        log.info("[候补选座结果] 处理: waitlistSn={}, success={}",
                waitlistSn, message.isSuccess());

        try {
            // 1. 查询候补订单
            var order = waitlistService.findByWaitlistSn(waitlistSn);
            if (order == null) {
                log.error("[候补选座结果] 候补订单不存在: waitlistSn={}", waitlistSn);
                return;
            }

            // 2. 状态检查
            if (order.getStatus() != 1) {
                log.warn("[候补选座结果] 订单状态非兑现中，跳过: waitlistSn={}, status={}",
                        waitlistSn, order.getStatus());
                return;
            }

            if (message.isSuccess() && message.getSelectedSeats() != null && !message.getSelectedSeats().isEmpty()) {
                // ==================== 选座成功 ====================
                handleSuccess(order, message);
            } else {
                // ==================== 选座失败 ====================
                handleFailure(order, message.getErrorMessage());
            }

        } catch (Exception e) {
            log.error("[候补选座结果] 处理异常: waitlistSn={}", waitlistSn, e);
            throw e;
        }
    }

    /**
     * 选座成功：发送订单创建请求
     */
    private void handleSuccess(WaitlistOrderDO order, SeatSelectionResultMessage result) {
        // 构建订单创建消息
        BigDecimal aPrice=order.getPrepayAmount().divide(BigDecimal.valueOf(result.getSelectedSeats().size()));
        OrderCreationRequestMessage orderMsg = new OrderCreationRequestMessage();
        orderMsg.setWaitlistSn(order.getWaitlistSn());
        orderMsg.setItems(result.getSelectedSeats()
                .stream()
                .map((item)->{
                    OrderCreationRequestMessage.OrderItem orderItem=new OrderCreationRequestMessage.OrderItem();
                    orderItem.setAmount(aPrice);
                    orderItem.setSeatType(item.getSeatType());
                    orderItem.setCarriageNumber(item.getCarriageNum());
                    orderItem.setSeatNumber(item.getSeatNum());
                    orderItem.setPassengerId(item.getPassengerId());
                    return orderItem;
                }).toList());
        orderMsg.setEndStation(order.getEndStation());
        orderMsg.setStartStation(order.getStartStation());
        orderMsg.setUsername(order.getUsername());
        orderMsg.setRunDate(order.getTravelDate());
        orderMsg.setTrainNum(order.getTrainNumber());

        // 发送订单创建请求
        messageQueueService.send(ORDER_CREATION_TOPIC, "create", orderMsg);

        log.info("[候补选座结果] 选座成功，发送订单创建请求: waitlistSn={}", order.getWaitlistSn());
    }

    /**
     * 选座失败：回滚状态，重新入队
     */
    private void handleFailure(WaitlistOrderDO order, String errorMsg) {
        // 更新状态为待兑现
        waitlistService.updateWaitlistStatus(order.getWaitlistSn(), 0);

        // 重新入队，优先级降低
        Double currentScore = waitlistQueueService.getScore(
                order.getWaitlistSn(), order.getTrainNumber(), order.getTravelDate().toString());
        if (currentScore != null) {
            BigDecimal newScore = BigDecimal.valueOf(currentScore).subtract(FAILURE_PENALTY);
            waitlistQueueService.updatePriority(
                    order.getWaitlistSn(), order.getTrainNumber(), order.getTravelDate().toString(), newScore);
            log.info("[候补选座结果] 选座失败，重新入队，优先级降低: waitlistSn={}, oldScore={}, newScore={}",
                    order.getWaitlistSn(), currentScore, newScore);
        } else {
            // 如果不在队列中，重新入队
            BigDecimal priority = priorityCalculator.calculatePriority(order,0, 1L,waitlistQueueService.getQueueSize(order.getTrainNumber(),order.getTravelDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),null));
            waitlistQueueService.enqueue(order, priority);
            log.info("[候补选座结果] 选座失败，重新入队: waitlistSn={}, priority={}",
                    order.getWaitlistSn(), priority);
        }

        log.warn("[候补选座结果] 选座失败: waitlistSn={}, reason={}",
                order.getWaitlistSn(), errorMsg);
    }
}
