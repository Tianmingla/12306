package com.lalal.modules.dto.response;

import com.lalal.modules.entity.TrainRoutePairDO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TrainSearchResponseDTO{
    /**
     * 方案ID（可选，用于前端标识）
     */
    private String planId;

    /**
     * 换乘次数（0 = 直达，1 = 一次中转，...）
     */
    private int transferCount;

    /**
     * 总耗时（分钟，便于前端计算）
     */
    private long totalDurationMinutes;

    /**
     * 首班车出发时间
     */
    private String firstDepartureTime;

    /**
     * 最终到达时间
     */
    private String finalArrivalTime;

    /**
     * 各种座位余票
     * */
    private List<Map<String,Integer>> remainingTicketNumMap=new ArrayList<>();

    /**
     * 各种座位票价
     * Key: 座位类型名称（如"二等座"、"硬座"等）
     * Value: 票价（元）
     */
    private List<Map<String, BigDecimal>> priceMap = new ArrayList<>();

    /**
     * 所有行程段（按顺序）
     */
    private List<TrainRoutePairDO> segments;
}
