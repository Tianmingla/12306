package com.lalal.modules.mq.config;

import com.lalal.modules.mq.MessageQueueService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 消息队列配置
 */

public class MqConfig {

    /**
     * 默认消息队列服务（使用空实现，具体实现由RocketMQ等提供）
     */
    @Bean
    @ConditionalOnMissingBean
    MessageQueueService defaultMessageQueueService() {
        return new EmptyMessageQueueService();
    }

    /**
     * 空实现，用于在没有具体MQ实现时避免启动错误
     */
    static class EmptyMessageQueueService implements MessageQueueService {

        @Override
        public void send(String topic, Object message) {
            throw new UnsupportedOperationException("No MQ implementation found. Please configure a concrete MQ implementation.");
        }

        @Override
        public void send(String topic, String tag, Object message) {
            throw new UnsupportedOperationException("No MQ implementation found. Please configure a concrete MQ implementation.");
        }

        @Override
        public void send(com.lalal.modules.mq.Message message) {
            throw new UnsupportedOperationException("No MQ implementation found. Please configure a concrete MQ implementation.");
        }

        @Override
        public void sendAsync(String topic, Object message, SendCallback callback) {
            throw new UnsupportedOperationException("No MQ implementation found. Please configure a concrete MQ implementation.");
        }

        @Override
        public void sendAsync(com.lalal.modules.mq.Message message, SendCallback callback) {
            throw new UnsupportedOperationException("No MQ implementation found. Please configure a concrete MQ implementation.");
        }

        @Override
        public void sendDelay(String topic, Object message, long delayTime) {
            throw new UnsupportedOperationException("No MQ implementation found. Please configure a concrete MQ implementation.");
        }

        @Override
        public void sendDelay(com.lalal.modules.mq.Message message, long delayTime) {
            throw new UnsupportedOperationException("No MQ implementation found. Please configure a concrete MQ implementation.");
        }

        @Override
        public void sendOrderly(String topic, String key, Object message) {
            throw new UnsupportedOperationException("No MQ implementation found. Please configure a concrete MQ implementation.");
        }

        @Override
        public void sendOrderly(com.lalal.modules.mq.Message message) {
            throw new UnsupportedOperationException("No MQ implementation found. Please configure a concrete MQ implementation.");
        }

        @Override
        public void registerListener(com.lalal.modules.mq.MessageListener listener) {
            throw new UnsupportedOperationException("No MQ implementation found. Please configure a concrete MQ implementation.");
        }

        @Override
        public void shutdown() {
            // 空实现
        }
    }
}
