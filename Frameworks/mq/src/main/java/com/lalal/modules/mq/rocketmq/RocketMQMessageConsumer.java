package com.lalal.modules.mq.rocketmq;

import com.lalal.modules.mq.Message;
import com.lalal.modules.mq.MessageListener;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RocketMQ 消息消费者示例
 *
 * 使用方式：
 * 1. 继承本类并实现抽象方法
 * 2. 或直接使用 @RocketMQMessageListener 注解创建消费者
 */
public abstract class RocketMQMessageConsumer<T> implements RocketMQListener<T>, MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RocketMQMessageConsumer.class);

    /**
     * RocketMQ 消息消费处理
     */
    @Override
    public void onMessage(T message) {
        try {
            // 将 RocketMQ 的消息转换为通用 Message 对象
            Message genericMessage = new Message(getTopic(), getTag(), message);
            boolean success = onMessage(genericMessage);

            if (!success) {
                log.warn("Message consumer returned false, will retry. Topic: {}, Tag: {}",
                        getTopic(), getTag());
            }
        } catch (Exception e) {
            log.error("Message consumer exception. Topic: {}, Tag: {}",
                    getTopic(), getTag(), e);
            onError(null, e);
            throw e; // 重新抛出异常，触发重试
        }
    }

    /**
     * 通用消息监听器接口实现（子类不需要实现）
     */
    @Override
    public boolean onMessage(Message message) {
        return handleMessage(message);
    }

    /**
     * 错误处理
     */
    @Override
    public void onError(Message message, Exception e) {
        log.error("Message consumer error. Topic: {}, Tag: {}, MessageId: {}",
                getTopic(), getTag(), message != null ? message.getMessageId() : "N/A", e);
    }

    /**
     * 处理消息（子类需要实现）
     * @param message 通用消息对象
     * @return true-成功，false-失败（会触发重试）
     */
    protected abstract boolean handleMessage(Message message);

    /**
     * 获取 Topic（子类需要实现，或通过 @RocketMQMessageListener 配置）
     */
    @Override
    public abstract String getTopic();

    /**
     * 获取 Tag（子类可选实现，默认null表示订阅所有）
     */
    @Override
    public String getTag() {
        return null;
    }

    /**
     * 获取消费者组名（子类可选实现）
     */
    @Override
    public String getConsumerGroup() {
        return null;
    }
}
