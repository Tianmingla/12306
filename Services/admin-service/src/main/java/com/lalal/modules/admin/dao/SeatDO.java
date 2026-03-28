package com.lalal.modules.admin.dao;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lalal.modules.base.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 座位实体（管理后台用）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_seat")
public class SeatDO extends BaseDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 列车ID
     */
    private Long trainId;

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
}
