package com.lalal.modules.admin.dao;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lalal.modules.base.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Date;

/**
 * 列车站点关系实体（线路管理）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_train_station")
public class TrainStationDO extends BaseDO {

    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 车次id
     */
    private Long trainId;

    /**
     * 冗余字段 车次号 方便业务 减少连表
     */
    private String trainNumber;

    /**
     * 车站id
     */
    private Long stationId;

    /**
     * 同理
     * 站台名
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
     * 停留时间，单位分
     */
    private Integer stopoverTime;
    /**
     *  运行日期
     */
    private LocalDate runDate;
    /**
     * 到达本站经过的天数
     */
    private Integer arriveDayDiff;
}

