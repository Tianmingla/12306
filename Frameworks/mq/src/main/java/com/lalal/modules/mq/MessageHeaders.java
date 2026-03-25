package com.lalal.modules.mq;

/**
 * 消息头常量
 */
public class MessageHeaders {

    /**
     * 消息来源
     */
    public static final String SOURCE = "source";

    /**
     * 重试次数
     */
    public static final String RETRY_COUNT = "retry-count";

    /**
     * 最大重试次数
     */
    public static final String MAX_RETRY_COUNT = "max-retry-count";

    /**
     * 业务类型
     */
    public static final String BUSINESS_TYPE = "business-type";

    /**
     * 追踪ID
     */
    public static final String TRACE_ID = "trace-id";

    /**
     * 时间戳
     */
    public static final String TIMESTAMP = "timestamp";

    private MessageHeaders() {
    }
}
