package com.lalal.modules.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 订单创建结果消息
 * order-service 发送到 ticket-service
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderCreationResultMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 全局请求ID
     */
    private String requestId;

    /**
     * 订单创建是否成功
     */
    private boolean success;

    /**
     * 成功时的订单号
     */
    private String orderSn;

    /**
     * 订单总金额
     */
    private BigDecimal totalAmount;

    /**
     * 失败时的错误信息
     */
    private String errorMessage;

    /**
     * 时间戳
     */
    private Long timestamp;
}
