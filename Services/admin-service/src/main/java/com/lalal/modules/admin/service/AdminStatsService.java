package com.lalal.modules.admin.service;

import com.lalal.modules.admin.dto.DashboardStats;

import java.util.List;
import java.util.Map;

public interface AdminStatsService {

    /**
     * 获取 Dashboard 统计数据
     */
    DashboardStats getDashboardStats();

    /**
     * 获取订单趋势数据
     */
    Map<String, Object> getOrderTrend(String startDate, String endDate, String type);

    /**
     * 获取列车类型分布
     */
    List<Map<String, Object>> getTrainTypeDistribution();

    /**
     * 获取热门线路
     */
    List<Map<String, Object>> getHotRoutes(Integer limit);
}
