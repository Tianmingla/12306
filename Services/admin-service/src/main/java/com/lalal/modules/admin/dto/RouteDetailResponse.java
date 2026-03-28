package com.lalal.modules.admin.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 线路详情响应
 */
@Data
public class RouteDetailResponse {

    /**
     * 列车ID
     */
    private Long trainId;

    /**
     * 车次号
     */
    private String trainNumber;

    /**
     * 起点站
     */
    private String startStation;

    /**
     * 终点站
     */
    private String endStation;

    /**
     * 经停站数量
     */
    private Integer stationCount;

    /**
     * 出发时间
     */
    private String departureTime;

    /**
     * 到达时间
     */
    private String arrivalTime;

    /**
     * 运行时长（分钟）
     */
    private Integer duration;

    /**
     * 经停站列表
     */
    private java.util.List<StationVO> stations;

    @Data
    public static class StationVO {
        private Long id;
        private Integer sequence;
        private String stationName;
        private String arrivalTime;
        private String departureTime;
        private Integer stopoverTime;
    }
}
