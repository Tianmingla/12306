package com.lalal.modules.mq.serializer;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.MessageConverter;

import java.nio.charset.StandardCharsets;

public class RocketMqSerializer extends BaseJsonMessageSerializer implements MessageConverter{
    @Override
    public Object fromMessage(Message<?> message, Class<?> targetClass) {
        // 反序列化过程 (消费端)
        byte[] payload = ((String) message.getPayload()).getBytes(StandardCharsets.UTF_8);
        return deserialize(payload);
    }

    @Override
    public Message<?> toMessage(Object payload, MessageHeaders headers) {
        // 序列化过程 (发送端)
        byte[] bytes = serialize(payload);
        return new org.springframework.messaging.support.GenericMessage<>(bytes, headers);
    }
}
