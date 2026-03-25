package com.lalal.modules.mq;

/**
 * 消息监听器接口
 */
public interface MessageListener {

    /**
     * 消息消费处理
     * @param message 消息对象
     * @return 消费结果：true-成功，false-失败（会触发重试）
     */
    boolean onMessage(Message message);

    /**
     * 消息消费失败处理
     * @param message 消息对象
     * @param e 异常信息
     */
    default void onError(Message message, Exception e) {
        // 默认不做处理
    }

    /**
     * 获取消费者所属的Topic
     * @return topic名称
     */
    String getTopic();

    /**
     * 获取消费者所属的Tag（可选）
     * @return tag名称，返回null表示订阅所有tag
     */
    default String getTag() {
        return null;
    }

    /**
     * 获取消费者组名称（可选）
     * @return 消费者组名称，返回null使用默认配置
     */
    default String getConsumerGroup() {
        return null;
    }
}
