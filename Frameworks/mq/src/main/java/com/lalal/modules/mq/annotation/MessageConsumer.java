package com.lalal.modules.mq.annotation;

import org.springframework.stereotype.Component;

import java.lang.annotation.*;

/**
 * 消息消费者注解
 * 简化 RocketMQ 消费者的创建，只需标注在类上即可自动注册为消费者
 *
 * 使用示例：
 * <pre>
 * {@code
 * @MessageConsumer(
 *     topic = "order-topic",
 *     tag = "create",
 *     consumerGroup = "order-create-consumer"
 * )
 * public class OrderCreateConsumer extends BaseMessageConsumer<OrderDTO> {
 *     @Override
 *     protected void doProcess(OrderDTO message) {
 *         // 处理订单创建逻辑
 *     }
 * }
 * }
 * </pre>
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface MessageConsumer {

    /**
     * 消费的主题
     */
    String topic();

    /**
     * 消费的标签（可选，默认订阅所有）
     */
    String tag() default "*";

    /**
     * 消费者组名
     */
    String consumerGroup();

    /**
     * 消费模式：顺序消费或并发消费
     */
    ConsumeMode consumeMode() default ConsumeMode.CONCURRENTLY;

    /**
     * 消息模型：集群消费或广播消费
     */
    MessageModel messageModel() default MessageModel.CLUSTERING;

    /**
     * 最大重试次数（默认16次）
     */
    int maxReconsumeTimes() default 16;

    /**
     * 消费模式枚举
     */
    enum ConsumeMode {
        /**
         * 并发消费
         */
        CONCURRENTLY,
        /**
         * 顺序消费
         */
        ORDERLY
    }

    /**
     * 消息模型枚举
     */
    enum MessageModel {
        /**
         * 集群消费：每条消息只会被一个消费者消费
         */
        CLUSTERING,
        /**
         * 广播消费：每条消息会被所有消费者消费
         */
        BROADCASTING
    }
}
