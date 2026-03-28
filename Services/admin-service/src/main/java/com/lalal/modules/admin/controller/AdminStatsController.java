package com.lalal.modules.admin.controller;

import com.lalal.modules.admin.dto.DashboardStats;
import com.lalal.modules.admin.service.AdminStatsService;
import com.lalal.modules.result.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/stats")
public class AdminStatsController {

    @Autowired
    private AdminStatsService adminStatsService;

    /**
     * 获取 Dashboard 统计数据
     */
    @GetMapping("/dashboard")
    public Result<DashboardStats> getDashboardStats() {
        return Result.success(adminStatsService.getDashboardStats());
    }

    /**
     * 获取订单趋势数据
     */
    @GetMapping("/order-trend")
    public Result<Map<String, Object>> getOrderTrend(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false, defaultValue = "day") String type) {
        return Result.success(adminStatsService.getOrderTrend(startDate, endDate, type));
    }

    /**
     * 获取列车类型分布
     */
    @GetMapping("/train-distribution")
    public Result<List<Map<String, Object>>> getTrainTypeDistribution() {
        return Result.success(adminStatsService.getTrainTypeDistribution());
    }

    /**
     * 获取热门线路
     */
    @GetMapping("/hot-routes")
    public Result<List<Map<String, Object>>> getHotRoutes(
            @RequestParam(required = false, defaultValue = "10") Integer limit) {
        return Result.success(adminStatsService.getHotRoutes(limit));
    }
}
