package com.lalal.modules.mq.rocketmq;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RocketMQ 配置属性
 */
@ConfigurationProperties(prefix = "rocketmq")
public class RocketMQProperties {

    /**
     * Name Server 地址
     */
    private String nameServer;

    /**
     * 生产者组名
     */
    private String producerGroup = "default-producer-group";

    /**
     * 消费者组名
     */
    private String consumerGroup = "default-consumer-group";

    /**
     * 消息发送超时时间（毫秒）
     */
    private Integer sendMsgTimeout = 3000;

    /**
     * 消息最大大小（字节）
     */
    private Integer maxMessageSize = 4 * 1024 * 1024;

    /**
     * 重试次数
     */
    private Integer retryTimesWhenSendFailed = 2;

    /**
     * 异步发送重试次数
     */
    private Integer retryTimesWhenSendAsyncFailed = 2;

    /**
     * 是否启用 VIP 通道
     */
    private Boolean vipChannelEnabled = true;

    /**
     * 消费者线程数最小值
     */
    private Integer consumeThreadMin = 20;

    /**
     * 消费者线程数最大值
     */
    private Integer consumeThreadMax = 64;

    // Getters and Setters

    public String getNameServer() {
        return nameServer;
    }

    public void setNameServer(String nameServer) {
        this.nameServer = nameServer;
    }

    public String getProducerGroup() {
        return producerGroup;
    }

    public void setProducerGroup(String producerGroup) {
        this.producerGroup = producerGroup;
    }

    public String getConsumerGroup() {
        return consumerGroup;
    }

    public void setConsumerGroup(String consumerGroup) {
        this.consumerGroup = consumerGroup;
    }

    public Integer getSendMsgTimeout() {
        return sendMsgTimeout;
    }

    public void setSendMsgTimeout(Integer sendMsgTimeout) {
        this.sendMsgTimeout = sendMsgTimeout;
    }

    public Integer getMaxMessageSize() {
        return maxMessageSize;
    }

    public void setMaxMessageSize(Integer maxMessageSize) {
        this.maxMessageSize = maxMessageSize;
    }

    public Integer getRetryTimesWhenSendFailed() {
        return retryTimesWhenSendFailed;
    }

    public void setRetryTimesWhenSendFailed(Integer retryTimesWhenSendFailed) {
        this.retryTimesWhenSendFailed = retryTimesWhenSendFailed;
    }

    public Integer getRetryTimesWhenSendAsyncFailed() {
        return retryTimesWhenSendAsyncFailed;
    }

    public void setRetryTimesWhenSendAsyncFailed(Integer retryTimesWhenSendAsyncFailed) {
        this.retryTimesWhenSendAsyncFailed = retryTimesWhenSendAsyncFailed;
    }

    public Boolean getVipChannelEnabled() {
        return vipChannelEnabled;
    }

    public void setVipChannelEnabled(Boolean vipChannelEnabled) {
        this.vipChannelEnabled = vipChannelEnabled;
    }

    public Integer getConsumeThreadMin() {
        return consumeThreadMin;
    }

    public void setConsumeThreadMin(Integer consumeThreadMin) {
        this.consumeThreadMin = consumeThreadMin;
    }

    public Integer getConsumeThreadMax() {
        return consumeThreadMax;
    }

    public void setConsumeThreadMax(Integer consumeThreadMax) {
        this.consumeThreadMax = consumeThreadMax;
    }
}
