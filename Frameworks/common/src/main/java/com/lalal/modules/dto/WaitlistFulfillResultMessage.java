package com.lalal.modules.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 候补兑现结果消息
 * Topic: waitlist-fulfillment-result-topic
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaitlistFulfillResultMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 请求ID
     */
    private String requestId;

    /**
     * 候补订单号
     */
    private String waitlistSn;

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 成功时：订单号
     */
    private String orderSn;

    /**
     * 失败时：错误信息
     */
    private String errorMessage;

    /**
     * 失败原因码
     * 1 - 无票
     * 2 - 支付失败
     * 3 - 座位冲突
     * 4 - 订单创建失败
     * 5 - 超时
     */
    private Integer failureReason;

    /**
     * 时间戳
     */
    private Long timestamp;
}
