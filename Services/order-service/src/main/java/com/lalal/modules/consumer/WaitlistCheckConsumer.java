package com.lalal.modules.consumer;

import com.lalal.modules.dto.WaitlistCheckMessage;
import com.lalal.modules.dto.WaitlistCheckResultMessage;
import com.lalal.modules.enumType.RequestStatus;
import com.lalal.modules.mq.MessageQueueService;
import com.lalal.modules.mq.annotation.MessageConsumer;
import com.lalal.modules.mq.rocketmq.RocketMQBaseConsumer;
import com.lalal.modules.service.WaitlistService;
import com.lalal.framework.cache.SafeCacheTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * 候补检查消费者
 *
 * <p>监听 waitlist-check-topic，检查候补订单是否有余票：
 * 1. 检查候补订单状态（是否过期/取消）
 * 2. 调用余票查询接口检查是否有票
 * 3. 有票：发送选座请求到 seat-selection-topic
 * 4. 无票：更新状态回"待兑现"，重新计算优先级
 *
 * @author Claude
 */
@Component
@Slf4j
@RequiredArgsConstructor
@MessageConsumer(
        topic = "waitlist-check-topic",
        tag = "check",
        consumerGroup = "waitlist-check-consumer"
)
@RocketMQMessageListener(
        topic = "waitlist-check-topic",
        consumerGroup = "waitlist-check-consumer",
        selectorExpression = "check"
)
public class WaitlistCheckConsumer extends RocketMQBaseConsumer<WaitlistCheckMessage> {

    private final WaitlistService waitlistService;
    private final SafeCacheTemplate safeCacheTemplate;
    private final MessageQueueService messageQueueService;

    private static final String SEAT_SELECTION_TOPIC = "seat-selection-topic";
    private static final String WAITLIST_CHECK_RESULT_TOPIC = "waitlist-check-result-topic";

    @Override
    protected void doProcess(WaitlistCheckMessage message) {
        String requestId = message.getRequestId();
        String waitlistSn = message.getWaitlistSn();

        log.info("[候补检查] 开始处理: requestId={}, waitlistSn={}", requestId, waitlistSn);

        try {
            // 1. 幂等性检查
            String msgIdKey = WaitlistCacheConstant.waitlistMessageIdKey(requestId);
            Boolean added = safeCacheTemplate.setIfAbsent(msgIdKey, "PROCESSING", 30, java.util.concurrent.TimeUnit.MINUTES);
            if (!added) {
                log.warn("[候补检查] 重复消息，跳过: requestId={}", requestId);
                return;
            }

            // 2. 查询候补订单
            var orderDO = waitlistService.findByWaitlistSn(waitlistSn);
            if (orderDO == null) {
                log.error("[候补检查] 候补订单不存在: waitlistSn={}", waitlistSn);
                sendEmptyResult(message, "候补订单不存在");
                return;
            }

            // 3. 检查订单状态（仅处理"待兑现"状态）
            if (orderDO.getStatus() != 0) {
                log.warn("[候补检查] 订单状态异常，跳过: waitlistSn={}, status={}",
                        waitlistSn, orderDO.getStatus());
                return;
            }

            // 4. 检查截止时间
            if (orderDO.getDeadline() != null && orderDO.getDeadline().before(new Date())) {
                waitlistService.updateWaitlistStatus(waitlistSn, 4); // 已过期
                log.info("[候补检查] 订单已过期: waitlistSn={}", waitlistSn);
                sendEmptyResult(message, "订单已过期");
                return;
            }

            // 5. 更新状态为"兑现中"
            waitlistService.updateWaitlistStatus(waitlistSn, 1);

            // 6. 检查余票（通过缓存或查询数据库）
            Integer availableCount = checkTicketAvailability(message);

            WaitlistCheckResultMessage resultMsg = WaitlistCheckResultMessage.builder()
                    .requestId(requestId)
                    .waitlistSn(waitlistSn)
                    .timestamp(System.currentTimeMillis())
                    .build();

            if (availableCount != null && availableCount > 0) {
                // 有票：发送选座请求
                resultMsg.setHasTicket(true);
                resultMsg.setAvailableCount(availableCount);

                sendSeatSelectionRequest(message);
                log.info("[候补检查] 发现余票，触发选座: waitlistSn={}, count={}",
                        waitlistSn, availableCount);
            } else {
                // 无票：回滚状态，重新计算优先级
                resultMsg.setHasTicket(false);
                resultMsg.setReason("当前无票，继续排队");

                waitlistService.updateWaitlistStatus(waitlistSn, 0); // 回待兑现
                waitlistService.recalculatePriority(orderDO);

                log.info("[候补检查] 当前无票，继续排队: waitlistSn={}", waitlistSn);
            }

            // 发送检查结果（可用于前端轮询）
            messageQueueService.send(WAITLIST_CHECK_RESULT_TOPIC, "result", resultMsg);

        } catch (Exception e) {
            log.error("[候补检查] 处理异常: requestId={}, waitlistSn={}",
                    requestId, waitlistSn, e);
            // 抛出异常触发 MQ 重试
            throw e;
        }
    }

    /**
     * 检查余票
     */
    private Integer checkTicketAvailability(WaitlistCheckMessage msg) {
        // 方式1：查询 Redis 余票缓存
        // Key 格式：TICKET:REMAINING::trainNum::date::seatType
        for (Integer seatType : msg.getSeatTypes()) {
            String cacheKey = String.format(
                    "TICKET:REMAINING::%s::%s::%d",
                    msg.getTrainNumber(),
                    msg.getTravelDate(),
                    seatType
            );

            Integer remaining = safeCacheTemplate.get(cacheKey, Integer.class);
            if (remaining != null && remaining > 0) {
                return remaining;
            }
        }

        // 方式2：查询数据库（实际应通过 Feign 调用 ticket-service）
        // TODO: 可以添加本地缓存减少 DB 查询

        return null; // 无缓存数据，保守返回 null
    }

    /**
     * 发送选座请求消息
     */
    private void sendSeatSelectionRequest(WaitlistCheckMessage msg) {
        // 构建 SeatSelectionRequestMessage（复用购票流程）
        var seatMsg = new com.lalal.modules.dto.SeatSelectionRequestMessage();
        seatMsg.setRequestId(msg.getRequestId());
        seatMsg.setUserId(msg.getUserId());
        seatMsg.setAccount(msg.getUsername());
        seatMsg.setTrainNum(msg.getTrainNumber());
        seatMsg.setStartStation(msg.getStartStation());
        seatMsg.setEndStation(msg.getEndStation());
        seatMsg.setDate(msg.getTravelDate());
        seatMsg.setPassengerIds(msg.getPassengerIds());
        seatMsg.setSeatTypelist(msg.getSeatTypes().stream()
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.toList()));
        seatMsg.setChooseSeats(null);
        seatMsg.setTimestamp(System.currentTimeMillis());
        seatMsg.setSource("WAITLIST"); // 标识来源为候补订单
        seatMsg.setWaitlistSn(msg.getWaitlistSn()); // 关联候补订单号

        messageQueueService.send(SEAT_SELECTION_TOPIC, "select", seatMsg);
        log.info("[候补检查] 已发送选座请求: requestId={}, waitlistSn={}",
                 msg.getRequestId(), msg.getWaitlistSn());
    }

    /**
     * 发送空结果（用于错误情况）
     */
    private void sendEmptyResult(WaitlistCheckMessage msg, String reason) {
        WaitlistCheckResultMessage result = WaitlistCheckResultMessage.builder()
                .requestId(msg.getRequestId())
                .waitlistSn(msg.getWaitlistSn())
                .hasTicket(false)
                .reason(reason)
                .timestamp(System.currentTimeMillis())
                .build();

        messageQueueService.send(WAITLIST_CHECK_RESULT_TOPIC, "result", result);
    }
}
