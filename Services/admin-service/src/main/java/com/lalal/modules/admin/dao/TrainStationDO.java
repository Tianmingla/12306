package com.lalal.modules.admin.dao;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lalal.modules.base.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 列车站点关系实体（线路管理）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_train_station")
public class TrainStationDO extends BaseDO {

    @TableId(type = IdType.AUTO)
    private Long id;

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
     * 站点顺序，从1开始
     */
    private Integer sequence;

    /**
     * 到站时间
     */
    private Date arrivalTime;

    /**
     * 出站时间
     */
    private Date departureTime;

    /**
     * 停留时间（分钟）
     */
    private Integer stopoverTime;

    /**
     * 列车运行日期
     */
    private Date runDate;
}
