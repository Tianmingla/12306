package com.lalal.modules.mq.serializer;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.lalal.modules.mq.Message;
import com.lalal.modules.mq.MessageSerializer;

import java.nio.charset.StandardCharsets;

/**
 * JSON消息序列化器
 */
public class BaseJsonMessageSerializer implements MessageSerializer {

    @Override
    public byte[] serialize(Object object) {
        if (object == null) {
            return null;
        }
        return JSON.toJSONString(object).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Message deserialize(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }

        JSONObject jsonObject=JSONObject.parseObject(new String(bytes, StandardCharsets.UTF_8));
        Message msg=jsonObject.toJavaObject(Message.class);
        try {
            Class<?> clazz = Class.forName(msg.getClazz());
            Object body=jsonObject.getJSONObject("body").toJavaObject(clazz);
            msg.setBody(body);
        }catch (Exception e){
            return null;
        }
        return msg;
    }
}
