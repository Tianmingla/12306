package com.lalal.modules.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 候补检查结果消息
 * Topic: waitlist-check-result-topic
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaitlistCheckResultMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 请求ID（对应 WaitlistCheckMessage.requestId）
     */
    private String requestId;

    /**
     * 候补订单号
     */
    private String waitlistSn;

    /**
     * 是否有票
     */
    private boolean hasTicket;

    /**
     * 可用票数
     */
    private Integer availableCount;

    /**
     * 无票原因（可选）
     */
    private String reason;

    /**
     * 时间戳
     */
    private Long timestamp;
}
