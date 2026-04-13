package com.lalal.modules.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 候补检查请求消息
 * Topic: waitlist-check-topic
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaitlistCheckMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 全局请求ID（幂等键）
     */
    private String requestId;

    /**
     * 候补订单号
     */
    private String waitlistSn;

    /**
     * 用户账号
     */
    private String username;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 车次号
     */
    private String trainNumber;
    /**
     * 车次id
     */
    private Integer trainId;

    /**
     * 出发站
     */
    private String startStation;

    /**
     * 到达站
     */
    private String endStation;

    /**
     * 乘车日期 yyyy-MM-dd
     */
    private String travelDate;

    /**
     * 座位类型列表
     */
    private List<Integer> seatTypes;
    /**
     * 乘车人id
     */
    private List<Long> passengerIds;

    /**
     * 优先级（1-100，越大越优先）
     */
    private Integer priority;

    /**
     * 截止时间
     */
    private Date deadline;

    /**
     * 预支付金额
     */
    private BigDecimal prepayAmount;

    /**
     * 时间戳
     */
    private Long timestamp;

    /**
     * 消息来源：WAITLIST/NORMAL
     */
    private String source;
}
