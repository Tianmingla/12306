package com.lalal.modules.consumer;

import com.lalal.modules.dto.WaitlistFulfillMessage;
import com.lalal.modules.entity.WaitlistOrderDO;
import com.lalal.modules.service.WaitlistQueueService;
import com.lalal.modules.service.WaitlistService;
import com.lalal.modules.mq.MessageQueueService;
import com.lalal.modules.mq.annotation.MessageConsumer;
import com.lalal.modules.mq.rocketmq.RocketMQBaseConsumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

/**
 * 候补兑现消费者
 *
 * <p>监听候补兑现主题，处理候补订单的兑现逻辑。
 *
 * <p>触发时机：
 * 1. 用户退票时 → 发送兑现消息 → 从队列出队最高优先级订单 → 尝试兑现
 * 2. 新增车次/座位时 → 发送兑现消息 → 从队列出队最高优先级订单 → 尝试兑现
 *
 * <p>核心流程：
 * 1. 从 Redis ZSet 队列出队(ZPOPMAX)最高优先级订单
 * 2. 查询候补订单，状态检查
 * 3. 更新状态为"兑现中"
 * 4. 调用选座服务尝试选座
 * 5. 选座成功 → 创建订单 → 更新状态为"已兑现" → 从队列移除
 * 6. 选座失败 → 更新状态为"待兑现" → 重新入队(优先级降低)
 *
 * @author Claude
 */
@Component
@Slf4j
@RequiredArgsConstructor
@MessageConsumer(
        topic = "waitlist-fulfill-topic",
        tag = "fulfill",
        consumerGroup = "waitlist-fulfill-consumer"
)
@RocketMQMessageListener(
        topic = "waitlist-fulfill-topic",
        consumerGroup = "waitlist-fulfill-consumer",
        selectorExpression = "fulfill"
)
public class WaitlistFulfillConsumer extends RocketMQBaseConsumer {

    private final WaitlistQueueService waitlistQueueService;
    private final WaitlistService waitlistService;
    private final MessageQueueService messageQueueService;

    private static final String SEAT_SELECTION_TOPIC = "seat-selection-topic";

    @Override
    protected void doProcess(Object msg) {
        WaitlistFulfillMessage message=(WaitlistFulfillMessage) msg;
        String trainNumber = message.getTrainNumber();
        String travelDate = message.getTravelDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        Integer seatType = message.getSeatType();

        log.info("[候补兑现] 收到兑现请求: trainNumber={}, travelDate={}, seatType={}",
                trainNumber, travelDate, seatType);

        try {
            // 1. 从队列出队最高优先级订单
            String waitlistSn = waitlistQueueService.dequeue(trainNumber, travelDate);

            if (waitlistSn == null) {
                log.info("[候补兑现] 队列为空，无需处理: trainNumber={}, travelDate={}",
                        trainNumber, travelDate);
                return;
            }

            // 2. 查询候补订单
            var order = waitlistService.findByWaitlistSn(waitlistSn);
            if (order == null) {
                log.error("[候补兑现] 候补订单不存在: waitlistSn={}", waitlistSn);
                return;
            }

            // 3. 状态检查（幂等性保护）
            if (order.getStatus() != 0) {
                log.warn("[候补兑现] 订单状态非待兑现，跳过: waitlistSn={}, status={}",
                        waitlistSn, order.getStatus());
                return;
            }

            // 4. 检查截止时间
            if (order.getDeadline() != null && order.getDeadline().before(new java.util.Date())) {
                waitlistService.updateWaitlistStatus(waitlistSn, 4); // 已过期
                log.info("[候补兑现] 候补订单已过期: waitlistSn={}", waitlistSn);
                return;
            }

            // 5. 更新状态为"兑现中"
            waitlistService.updateWaitlistStatus(waitlistSn, 1);

            // 6. 发送选座请求
            sendSeatSelectionRequest(order);

            log.info("[候补兑现] 已发送选座请求: waitlistSn={}", waitlistSn);

        } catch (Exception e) {
            log.error("[候补兑现] 处理异常: trainNumber={}, travelDate={}",
                    trainNumber, travelDate, e);
            throw e;
        }
    }

    /**
     * 发送选座请求消息
     */
    private void sendSeatSelectionRequest(WaitlistOrderDO order) {
        var seatMsg = new com.lalal.modules.dto.SeatSelectionRequestMessage();
        seatMsg.setRequestId(java.util.UUID.randomUUID().toString().replace("-", ""));
        seatMsg.setAccount(order.getUsername());
        seatMsg.setTrainNum(order.getTrainNumber());
        seatMsg.setStartStation(order.getStartStation());
        seatMsg.setEndStation(order.getEndStation());
        seatMsg.setDate(order.getTravelDate().toString());
        seatMsg.setPassengerIds(java.util.Arrays.stream(order.getPassengerIds().split(","))
                .map(Long::parseLong)
                .collect(java.util.stream.Collectors.toList()));
        seatMsg.setSeatTypelist(java.util.Arrays.stream(order.getSeatTypes().split(","))
                .collect(java.util.stream.Collectors.toList()));
        seatMsg.setTimestamp(System.currentTimeMillis());
        seatMsg.setSource("WAITLIST"); // 标记来源为候补订单
        seatMsg.setWaitlistSn(order.getWaitlistSn());

        messageQueueService.send(SEAT_SELECTION_TOPIC, "select", seatMsg);
    }
}
