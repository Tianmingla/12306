package com.lalal.modules.mq.rocketmq;

import com.lalal.modules.mq.Message;
import com.lalal.modules.mq.annotation.MessageConsumer;
import com.lalal.modules.mq.consumer.BaseMessageConsumer;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 注解驱动的 RocketMQ 消费者适配器
 *
 * <p>此抽象类结合 @MessageConsumer 注解使用，自动生成 @RocketMQMessageListener 配置。
 * 子类只需继承此基类并实现 doProcess 方法即可。</p>
 *
 * <p>使用示例：</p>
 * <pre>
 * {@code
 * @MessageConsumer(
 *     topic = "order-topic",
 *     tag = "create",
 *     consumerGroup = "order-create-consumer",
 *     consumeMode = ConsumeMode.CONCURRENTLY
 * )
 * public class OrderCreateConsumer extends RocketMQBaseConsumer<OrderDTO> {
 *
 *     @Override
 *     protected void doProcess(OrderDTO message) {
 *         // 处理订单创建逻辑
 *     }
 * }
 * }
 * </pre>
 *
 * @param <T> 消息体类型
 */
public abstract class RocketMQBaseConsumer<T> extends BaseMessageConsumer<T> implements RocketMQListener<T> {

    private final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * RocketMQ 消息消费入口
     * 实现 RocketMQListener 接口的方法
     */
    @Override
    public void onMessage(T message) {
        try {
            // 构建 Message 对象
            Message msg = new Message();
            msg.setBody(message);

            // 调用父类处理方法
            boolean success = process(msg);

            if (!success) {
                log.warn("消息处理返回失败，RocketMQ 将重试。消息体类型: {}",
                        message != null ? message.getClass().getSimpleName() : "null");
            }

        } catch (Exception e) {
            log.error("消息处理异常，RocketMQ 将重试。消息体类型: {}",
                    message != null ? message.getClass().getSimpleName() : "null", e);
            // 抛出异常触发 RocketMQ 重试
            throw e;
        }
    }
}
