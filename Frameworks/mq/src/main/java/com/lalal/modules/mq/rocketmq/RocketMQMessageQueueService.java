package com.lalal.modules.mq.rocketmq;

import com.alibaba.fastjson2.JSON;
import com.lalal.modules.mq.Message;
import com.lalal.modules.mq.MessageListener;
import com.lalal.modules.mq.MessageQueueService;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * RocketMQ 消息队列服务实现
 * 为什么包装消息一般rocketTemplate这样的包装都会包装消息 但不会发送只会发送负载
 * 这里发送整个包装消息是为了消费处理的时候做一些额外逻辑
 */
public class RocketMQMessageQueueService implements MessageQueueService {

    private static final Logger log = LoggerFactory.getLogger(RocketMQMessageQueueService.class);


    private RocketMQTemplate rocketMQTemplate;

    public RocketMQMessageQueueService(RocketMQTemplate rocketMQTemplate){
        this.rocketMQTemplate=rocketMQTemplate;
    }

    @Override
    public void send(String topic, Object message) {
        send(topic, null, message);
    }

    @Override
    public void send(String topic, String tag, Object message) {
        send(new Message(topic, tag, message));
    }

    @Override
    public void send(Message message) {
        String destination = buildDestination(message.getTopic(), message.getTag());
        try {
            SendResult sendResult = rocketMQTemplate.syncSend(destination, message);
            if (sendResult.getSendStatus() != SendStatus.SEND_OK) {
                log.error("RocketMQ send failed. MessageId: {}, SendStatus: {}",
                        message.getMessageId(), sendResult.getSendStatus());
                throw new RuntimeException("RocketMQ send failed: " + sendResult.getSendStatus());
            }
            log.debug("RocketMQ send success. MessageId: {}, Topic: {}",
                    message.getMessageId(), message.getTopic());
        } catch (Exception e) {
            log.error("RocketMQ send exception. MessageId: {}, Topic: {}",
                    message.getMessageId(), message.getTopic(), e);
            throw new RuntimeException("RocketMQ send exception", e);
        }
    }

    @Override
    public void sendAsync(String topic, Object message, SendCallback callback) {
        sendAsync(new Message(topic, message), callback);
    }

    @Override
    public void sendAsync(Message message, SendCallback callback) {
        String destination = buildDestination(message.getTopic(), message.getTag());
        rocketMQTemplate.asyncSend(destination,message,new org.apache.rocketmq.client.producer.SendCallback(){
            @Override
            public void onSuccess(SendResult sendResult) {
                log.debug("RocketMQ async send success. MessageId: {}", message.getMessageId());
                if (callback != null) {
                    callback.onSuccess(message);
                }
            }

            @Override
            public void onException(Throwable throwable) {
                log.error("RocketMQ async send failed. MessageId: {}", message.getMessageId());
                if (callback != null) {
                    callback.onException(message, new RuntimeException());
                }
            }
        });
    }

    @Override
    public void sendDelay(String topic, Object message, long delayTime) {
        Message msg = new Message(topic, message);
        msg.setDelayTime(delayTime);
        sendDelay(msg, delayTime);
    }

    @Override
    public void sendDelay(String topic, String tag, Object message, long delayTime) {
        Message msg = new Message(topic,tag, message);
        msg.setDelayTime(delayTime);
        sendDelay(msg,delayTime);
    }

    @Override
    public void sendDelay(Message message, long delayTime) {
        String destination = buildDestination(message.getTopic(), message.getTag());
        // RocketMQ 延迟等级：1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m 1h 2h
        // 需要将延迟时间映射到对应的等级
        int delayLevel = calculateDelayLevel(delayTime);
//        try {
//            SendResult sendResult = rocketMQTemplate.syncSend(destination, message,
//                    3000, delayLevel);
//            log.debug("RocketMQ delay send success. MessageId: {}, DelayTime: {}ms",
//                    message.getMessageId(), delayTime);
//        } catch (Exception e) {
//            log.error("RocketMQ delay send exception. MessageId: {}, DelayTime: {}ms",
//                    message.getMessageId(), delayTime, e);
//            throw new RuntimeException("RocketMQ delay send exception", e);
//        }
    }

    @Override
    public void sendOrderly(String topic, String key, Object message) {
        Message msg = new Message(topic, message);
        msg.setKey(key);
        sendOrderly(msg);
    }

    @Override
    public void sendOrderly(Message message) {
        String destination = buildDestination(message.getTopic(), message.getTag());
        String hashKey = message.getKey();
        if (hashKey == null) {
            hashKey = message.getMessageId();
        }
        try {
            SendResult sendResult = rocketMQTemplate.syncSendOrderly(destination,
                    message, hashKey);
            log.debug("RocketMQ orderly send success. MessageId: {}, Key: {}",
                    message.getMessageId(), message.getKey());
        } catch (Exception e) {
            log.error("RocketMQ orderly send exception. MessageId: {}, Key: {}",
                    message.getMessageId(), message.getKey(), e);
            throw new RuntimeException("RocketMQ orderly send exception", e);
        }
    }

    @Override
    public void registerListener(MessageListener listener) {
        // RocketMQ 的监听器通过注解方式配置，这里只记录日志
        log.info("MessageListener registered. Topic: {}, Tag: {}",
                listener.getTopic(), listener.getTag());
        // 具体的监听器实现需要在服务中使用 @RocketMQMessageListener 注解
    }

    @Override
    public void shutdown() {
        // RocketMQTemplate 会自动管理生命周期
        log.info("RocketMQMessageQueueService shutdown.");
    }

    /**
     * 构建目标地址
     * 格式：topic:tag
     */
    private String buildDestination(String topic, String tag) {
        if (tag == null || tag.isEmpty()) {
            return topic;
        }
        return topic + ":" + tag;
    }

    /**
     * 计算延迟等级
     * RocketMQ 延迟等级对照表：
     * level 1: 1s, level 2: 5s, level 3: 10s, level 4: 30s
     * level 5: 1m, level 6: 2m, level 7: 3m, level 8: 4m
     * level 9: 5m, level 10: 6m, level 11: 7m, level 12: 8m
     * level 13: 9m, level 14: 10m, level 15: 20m, level 16: 30m
     * level 17: 1h, level 18: 2h
     */
    private int calculateDelayLevel(long delayTimeMs) {
        long delaySeconds = delayTimeMs / 1000;

        if (delaySeconds < 1) return 1;
        if (delaySeconds < 5) return 2;
        if (delaySeconds < 10) return 3;
        if (delaySeconds < 30) return 4;
        if (delaySeconds < 60) return 5;
        if (delaySeconds < 120) return 6;
        if (delaySeconds < 180) return 7;
        if (delaySeconds < 240) return 8;
        if (delaySeconds < 300) return 9;
        if (delaySeconds < 360) return 10;
        if (delaySeconds < 420) return 11;
        if (delaySeconds < 480) return 12;
        if (delaySeconds < 540) return 13;
        if (delaySeconds < 600) return 14;
        if (delaySeconds < 1200) return 15;
        if (delaySeconds < 1800) return 16;
        if (delaySeconds < 3600) return 17;

        return 18; // 最大2小时
    }
}
