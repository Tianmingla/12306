package com.lalal.modules.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

/**
 * 订单创建请求消息
 * ticket-service 发送到 order-service
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderCreationRequestMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 全局请求ID
     */
    private String requestId;

    /**
     * 候补订单号（候补订单创建时使用）
     */
    private String waitlistSn;

    /**
     * 车次号
     */
    private String trainNum;

    /**
     * 出发站
     */
    private String startStation;

    /**
     * 到达站
     */
    private String endStation;
    /**
     * 用户账号(手机号)
     */
    private String username;

    /**
     * 乘车日期
     */
    private LocalDate runDate;

    /**
     * 计划发车时间（毫秒时间戳）
     */
    private Long planDepartTime;

    /**
     * 计划到达时间（毫秒时间戳）
     */
    private Long planArrivalTime;

    /**
     * 订单项列表
     */
    private List<OrderItem> items;

    /**
     * 订单项
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class OrderItem implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * 乘车人ID
         */
        private Long passengerId;

        /**
         * 车厢号
         */
        private String carriageNumber;

        /**
         * 座位号
         */
        private String seatNumber;

        /**
         * 座位类型
         */
        private Integer seatType;

        /**
         * 票价
         */
        private BigDecimal amount;

        /**
         * 真实姓名
         */
        private String realName;

        /**
         * 身份证号
         */
        private String idCard;
    }
}
