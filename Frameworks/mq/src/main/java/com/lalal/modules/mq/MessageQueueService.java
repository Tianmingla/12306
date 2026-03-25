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
     * 发送消息（带Tag）
     * @param topic 主题
     * @param tag 标签
     * @param message 消息内容
     */
    void send(String topic, String tag, Object message);

    /**
     * 发送消息（使用Message对象）
     * @param message 消息对象
     */
    void send(Message message);

    /**
     * 异步发送消息
     * @param topic 主题
     * @param message 消息内容
     * @param callback 发送回调
     */
    void sendAsync(String topic, Object message, SendCallback callback);

    /**
     * 异步发送消息（使用Message对象）
     * @param message 消息对象
     * @param callback 发送回调
     */
    void sendAsync(Message message, SendCallback callback);

    /**
     * 发送延迟消息
     * @param topic 主题
     * @param message 消息内容
     * @param delayTime 延迟时间（毫秒）
     */
    void sendDelay(String topic, Object message, long delayTime);

    /**
     * 发送延迟消息（使用Message对象）
     * @param message 消息对象
     * @param delayTime 延迟时间（毫秒）
     */
    void sendDelay(Message message, long delayTime);

    /**
     * 发送顺序消息
     * @param topic 主题
     * @param key 消息键（用于保证顺序）
     * @param message 消息内容
     */
    void sendOrderly(String topic, String key, Object message);

    /**
     * 发送顺序消息（使用Message对象）
     * @param message 消息对象
     */
    void sendOrderly(Message message);

    /**
     * 注册消息监听器
     * @param listener 监听器
     */
    void registerListener(MessageListener listener);

    /**
     * 关闭消息队列服务
     */
    void shutdown();

    /**
     * 异步发送回调接口
     */
    interface SendCallback {
        /**
         * 发送成功
         */
        void onSuccess(Message message);

        /**
         * 发送失败
         * @param e 异常信息
         */
        void onException(Message message, Exception e);
    }
}
