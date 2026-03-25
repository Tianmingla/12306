package com.lalal.modules.mq.serializer;

import com.alibaba.fastjson2.JSON;
import com.lalal.modules.mq.MessageSerializer;

import java.nio.charset.StandardCharsets;

/**
 * JSON消息序列化器
 */
public class JsonMessageSerializer implements MessageSerializer {

    @Override
    public byte[] serialize(Object object) {
        if (object == null) {
            return null;
        }
        return JSON.toJSONString(object).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T deserialize(byte[] bytes, Class<T> clazz) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        String json = new String(bytes, StandardCharsets.UTF_8);
        return JSON.parseObject(json, clazz);
    }
}
