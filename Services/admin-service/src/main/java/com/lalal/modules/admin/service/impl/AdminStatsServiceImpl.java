package com.lalal.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lalal.framework.cache.RedisSerializer.ValueRedisSerializer;
import com.lalal.framework.cache.SafeCacheTemplate;
import com.lalal.modules.admin.dao.OrderDO;
import com.lalal.modules.admin.dto.DashboardStats;
import com.lalal.modules.admin.mapper.OrderMapper;
import com.lalal.modules.admin.mapper.StationMapper;
import com.lalal.modules.admin.mapper.TrainMapper;
import com.lalal.modules.admin.mapper.UserMapper;
import com.lalal.modules.admin.service.AdminStatsService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class AdminStatsServiceImpl implements AdminStatsService {

    private static final String CACHE_KEY_DASHBOARD = "admin:stats:dashboard";
    private static final String CACHE_KEY_TRAIN_DIST = "admin:stats:train:distribution";
    private static final String CACHE_KEY_HOT_ROUTES = "admin:stats:hot:routes";

    @Autowired
    private SafeCacheTemplate cacheTemplate;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private TrainMapper trainMapper;

    @Autowired
    private StationMapper stationMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Value("${admin.cache.dashboard-ttl:300}")
    private Long dashboardTtl;

    @Value("${admin.cache.train-distribution-ttl:86400}")
    private Long trainDistTtl;

    @Value("${admin.cache.stats-ttl:3600}")
    private Long statsTtl;

    @Override
    public DashboardStats getDashboardStats() {
        return cacheTemplate.setDefaultValueSerializer()
                .safeGet(CACHE_KEY_DASHBOARD,
                        this::calculateDashboardStats,
                        new TypeReference<DashboardStats>() {},
                        dashboardTtl,
                        java.util.concurrent.TimeUnit.SECONDS);
    }

    private DashboardStats calculateDashboardStats() {
        DashboardStats stats = new DashboardStats();

        // 用户总数
        Long totalUsers = userMapper.selectCount(
                new LambdaQueryWrapper<com.lalal.modules.admin.dao.UserDO>()
                        .eq(com.lalal.modules.admin.dao.UserDO::getDelFlag, 0));
        stats.setTotalUsers(totalUsers);

        // 列车总数
        Long totalTrains = trainMapper.selectCount(
                new LambdaQueryWrapper<com.lalal.modules.admin.dao.TrainDO>()
                        .eq(com.lalal.modules.admin.dao.TrainDO::getDelFlag, 0));
        stats.setTotalTrains(totalTrains);

        // 车站总数
        Long totalStations = stationMapper.selectCount(
                new LambdaQueryWrapper<com.lalal.modules.admin.dao.StationDO>()
                        .eq(com.lalal.modules.admin.dao.StationDO::getDelFlag, 0));
        stats.setTotalStations(totalStations);

        // 订单总数
        Long totalOrders = orderMapper.selectCount(
                new LambdaQueryWrapper<com.lalal.modules.admin.dao.OrderDO>()
                        .eq(com.lalal.modules.admin.dao.OrderDO::getDelFlag, 0));
        stats.setTotalOrders(totalOrders);

        // 今日订单统计（简化实现）
        stats.setTodayTickets(0L);
        stats.setTodayAmount(BigDecimal.ZERO);
        stats.setUserGrowth(BigDecimal.ZERO);
        stats.setOrderGrowth(BigDecimal.ZERO);

        return stats;
    }

    @Override
    public Map<String, Object> getOrderTrend(String startDate, String endDate, String type) {
        Map<String, Object> result = new HashMap<>();

        // 1. 处理时间范围
        LocalDate start = startDate != null ? LocalDate.parse(startDate) : LocalDate.now().minusDays(7);
        LocalDate end = endDate != null ? LocalDate.parse(endDate) : LocalDate.now();

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        DateTimeFormatter displayFormatter = DateTimeFormatter.ofPattern("MM-dd");

        // 2. 准备“全量日期”容器，用于解决数据缺失导致的前端图表错位问题
        // Key: "2026-03-27", Value: 0 (初始化为0)
        Map<String, Integer> orderMap = new LinkedHashMap<>();
        Map<String, BigDecimal> amountMap = new LinkedHashMap<>();

        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            String dateStr = date.format(dateFormatter);
            orderMap.put(dateStr, 0);
            amountMap.put(dateStr, BigDecimal.ZERO);
        }

        // 4. 执行查询
//        List<Map<String, Object>> dbResults = cacheTemplate.safeGet(
//                "admin:satus:orderTrend:"+end.format(dateFormatter),
//                ()->{
//                    // 构建查询
//                    QueryWrapper<OrderDO> wrapper = new QueryWrapper<>();
//                    wrapper.select(
//                                    "DATE(update_time) as date",
//                                    "COUNT(*) as count",
//                                    "SUM(IFNULL(total_amount, 0)) as total_money")
//                            .ge("update_time", start.atStartOfDay())
//                            .le("update_time", end.atTime(23, 59, 59))
//                            .groupBy("DATE(update_time)")
//                            .orderByAsc("DATE(update_time)");
//                    return orderMapper.selectMaps(wrapper);
//                },
//                new TypeReference<List<Map<String,Object>>>(){},
//                1,
//                TimeUnit.DAYS
//        );
        QueryWrapper<OrderDO> wrapper = new QueryWrapper<>();
                    wrapper.select(
                                    "DATE(update_time) as date",
                                    "COUNT(*) as count",
                                    "SUM(IFNULL(total_amount, 0)) as total_money")
                            .ge("update_time", start.atStartOfDay())
                            .le("update_time", end.atTime(23, 59, 59))
                            .groupBy("DATE(update_time)")
                            .orderByAsc("DATE(update_time)");
        List<Map<String, Object>> dbResults = orderMapper.selectMaps(wrapper);


        // 5. 将数据库结果“填”入全量日期容器中
        for (Map<String, Object> row : dbResults) {
            String dateKey = ((Date) row.get("date")).toInstant()
                    .atZone(ZoneId.systemDefault())
                    .format(dateFormatter);
            if (orderMap.containsKey(dateKey)) {
                Long count = (Long) row.get("count");
                orderMap.put(dateKey, count.intValue());

                // 填充金额
                BigDecimal amount = (BigDecimal) row.get("total_money");
                // 如果数据库没数据，SUM可能会返回null，这里做个非空判断
                amountMap.put(dateKey, amount != null ? amount : BigDecimal.ZERO);
            }
        }

        // 6. 组装最终返回结果（分离 Key 和 Value）
        List<String> dates = new ArrayList<>();
        List<Integer> orders = new ArrayList<>();
        List<BigDecimal> amounts = new ArrayList<>();

        for (String dateKey : orderMap.keySet()) {
            // 格式化日期给前端 (例如 03-27)
            LocalDate parseDate = LocalDate.parse(dateKey);
            dates.add(parseDate.format(displayFormatter));

            orders.add(orderMap.get(dateKey));
            amounts.add(amountMap.get(dateKey));
        }

        result.put("dates", dates);
        result.put("orders", orders);
        result.put("amounts", amounts);

        return result;
    }

    @Override
    public List<Map<String, Object>> getTrainTypeDistribution() {
        return cacheTemplate.setDefaultValueSerializer()
                .safeGet(CACHE_KEY_TRAIN_DIST,
                        this::calculateTrainTypeDistribution,
                        new TypeReference<List<Map<String, Object>>>() {},
                        trainDistTtl,
                        java.util.concurrent.TimeUnit.SECONDS);
    }

    private List<Map<String, Object>> calculateTrainTypeDistribution() {
        List<Map<String, Object>> result = new ArrayList<>();

        // 按列车类型分组统计
        String[] types = {"高铁", "动车", "普通车"};
        String[] names = {"高铁(G/C)", "动车(D)", "普通车(Z/T/K)"};

        for (int i = 0; i < types.length; i++) {
            Map<String, Object> item = new HashMap<>();
            item.put("name", names[i]);
            Long count = trainMapper.selectCount(
                    new LambdaQueryWrapper<com.lalal.modules.admin.dao.TrainDO>()
                            .eq(com.lalal.modules.admin.dao.TrainDO::getTrainType, i)
                            .eq(com.lalal.modules.admin.dao.TrainDO::getDelFlag, 0));
            item.put("value", count);
            result.add(item);
        }

        return result;
    }

    @Override
    public List<Map<String, Object>> getHotRoutes(Integer limit) {
        int queryLimit = limit != null && limit > 0 ? limit : 10;

        String cacheKey = CACHE_KEY_HOT_ROUTES + ":" + queryLimit;

        return cacheTemplate.setDefaultValueSerializer()
                .safeGet(cacheKey,
                        () -> calculateHotRoutes(queryLimit),
                        new TypeReference<List<Map<String, Object>>>() {},
                        statsTtl,
                        java.util.concurrent.TimeUnit.SECONDS);
    }

    private List<Map<String, Object>> calculateHotRoutes(int limit) {
        // 简化实现：返回模拟数据
        // 实际应从 t_order 表按 start_station + end_station 分组统计
        List<Map<String, Object>> result = new ArrayList<>();

        String[][] routes = {
                {"北京-上海", "1523"},
                {"广州-深圳", "892"},
                {"成都-重庆", "756"},
                {"武汉-长沙", "623"},
                {"杭州-上海", "512"}
        };

        for (int i = 0; i < Math.min(limit, routes.length); i++) {
            Map<String, Object> item = new HashMap<>();
            item.put("route", routes[i][0]);
            item.put("count", Integer.parseInt(routes[i][1]));
            item.put("growth", Math.random() * 20 - 10); // -10% ~ +10%
            result.add(item);
        }

        return result;
    }
}
