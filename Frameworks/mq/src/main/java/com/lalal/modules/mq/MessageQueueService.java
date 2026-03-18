package com.lalal.modules.mq;

/**
 * 消息队列服务接口，用于削峰填谷
 */
public interface MessageQueueService {
    
    /**
     * 发送消息
     * @param topic 主题
     * @param message 消息内容
     */
    void send(String topic, Object message);
    
    /**
     * 发送延迟消息
     * @param topic 主题
     * @param message 消息内容
     * @param delayTime 延迟时间（毫秒）
     */
    void sendDelay(String topic, Object message, long delayTime);
    
    // TODO: 更多MQ操作，如顺序消息、事务消息等
}
