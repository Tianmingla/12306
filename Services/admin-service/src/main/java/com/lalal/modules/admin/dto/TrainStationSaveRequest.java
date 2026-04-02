package com.lalal.modules.admin.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Date;

/**
 * 经停站保存请求
 */
@Data
public class TrainStationSaveRequest {

    /**
     * 列车ID
     */
    private Long trainId;

    /**
     * 车次号
     */
    private String trainNumber;

    /**
     * 车站ID
     */
    private Long stationId;

    /**
     * 车站名称
     */
    private String stationName;

    /**
     * 站点顺序
     */
    private Integer sequence;

    /**
     * 到站时间
     */
    private LocalTime arrivalTime;

    /**
     * 出站时间
     */
    private LocalTime departureTime;

    /**
     * 停留时间（分钟）
     */
    private Integer stopoverTime;

    /**
     * 运行日期
     */
    private LocalDate runDate;
}
