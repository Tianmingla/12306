package com.lalal.modules.consumer;

import com.lalal.modules.dto.ReminderMessage;
import com.lalal.modules.mq.annotation.MessageConsumer;
import com.lalal.modules.mq.rocketmq.RocketMQBaseConsumer;
import com.lalal.modules.service.ReminderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.springframework.stereotype.Component;

/**
 * 出行提醒消费者
 * 监听 travel-reminder-topic
 *
 * 核心逻辑：
 * 1. 接收延迟消息
 * 2. 校验版本号
 * 3. 决定是否发送短信或重新调度
 */
@Component
@Slf4j
@RequiredArgsConstructor
@MessageConsumer(
    topic = "travel-reminder-topic",
    tag = "*",
    consumerGroup = "travel-reminder-consumer"
)
@RocketMQMessageListener(
    topic = "travel-reminder-topic",
    consumerGroup = "travel-reminder-consumer",
    selectorExpression = "*"
)
public class ReminderConsumer extends RocketMQBaseConsumer {

    private final ReminderService reminderService;

    @Override
    protected void doProcess(Object msg) {
        ReminderMessage message=(ReminderMessage) msg;
        log.info("[提醒消费] 收到消息: orderSn={}, type={}, version={}",
                message.getOrderSn(), message.getReminderType(), message.getVersion());

        try {
            reminderService.processReminder(message);
        } catch (Exception e) {
            log.error("[提醒消费] 处理失败: orderSn={}", message.getOrderSn(), e);
            throw e; // 触发重试
        }
    }
}