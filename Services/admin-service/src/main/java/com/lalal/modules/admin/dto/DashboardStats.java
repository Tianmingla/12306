package com.lalal.modules.admin.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Dashboard 统计数据
 */
@Data
public class DashboardStats {

    /**
     * 用户总数
     */
    private Long totalUsers;

    /**
     * 订单总数
     */
    private Long totalOrders;

    /**
     * 列车总数
     */
    private Long totalTrains;

    /**
     * 车站总数
     */
    private Long totalStations;

    /**
     * 今日售票数
     */
    private Long todayTickets;

    /**
     * 今日销售额
     */
    private BigDecimal todayAmount;

    /**
     * 用户增长率
     */
    private BigDecimal userGrowth;

    /**
     * 订单增长率
     */
    private BigDecimal orderGrowth;
}
