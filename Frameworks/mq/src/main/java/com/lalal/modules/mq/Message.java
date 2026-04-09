package com.lalal.modules.mq;

import lombok.Data;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 通用消息体
 */
@Data
public class Message implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 消息唯一ID
     */
    private String messageId;

    /**
     * 主题
     */
    private String topic;

    /**
     * 标签（RocketMQ使用）
     */
    private String tag;

    /**
     * 消息键（用于顺序消息）
     */
    private String key;

    /**
     * 消息头（扩展信息）
     */
    private Map<String, Object> headers;

    /**
     * 消息体内容
     */
    private Object body;
    /**
     * 类型信息
     */
    private String clazz;

    /**
     * 延迟时间（毫秒）
     */
    private Long delayTime;

    /**
     * 消息发送时间戳
     */
    private Long sendTime;

    public Message() {
        this.messageId = UUID.randomUUID().toString();
        this.headers = new HashMap<>();
        this.sendTime = System.currentTimeMillis();
    }

    public Message(String topic, Object body) {
        this();
        this.topic = topic;
        this.body = body;
        this.clazz=body.getClass().getName();
    }

    public Message(String topic, String tag, Object body) {
        this(topic, body);
        this.tag = tag;
    }

    public Message(String topic, String tag, String key, Object body) {
        this(topic, tag, body);
        this.key = key;
    }

    public String getMessageId() {
        return messageId;
    }

    public Message setMessageId(String messageId) {
        this.messageId = messageId;
        return this;
    }

    public String getTopic() {
        return topic;
    }

    public Message setTopic(String topic) {
        this.topic = topic;
        return this;
    }

    public String getTag() {
        return tag;
    }

    public Message setTag(String tag) {
        this.tag = tag;
        return this;
    }

    public String getKey() {
        return key;
    }

    public Message setKey(String key) {
        this.key = key;
        return this;
    }

    public Map<String, Object> getHeaders() {
        return headers;
    }

    public Message setHeaders(Map<String, Object> headers) {
        this.headers = headers;
        return this;
    }

    public Message addHeader(String name, Object value) {
        this.headers.put(name, value);
        return this;
    }

    public Object getBody() {
        return body;
    }

    public Message setBody(Object body) {
        this.body = body;
        this.clazz=body.getClass().getName();
        return this;
    }

    public Long getDelayTime() {
        return delayTime;
    }

    public Message setDelayTime(Long delayTime) {
        this.delayTime = delayTime;
        return this;
    }

    public Long getSendTime() {
        return sendTime;
    }

    public Message setSendTime(Long sendTime) {
        this.sendTime = sendTime;
        return this;

    }
}
