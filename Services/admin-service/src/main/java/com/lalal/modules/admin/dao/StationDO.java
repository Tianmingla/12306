package com.lalal.modules.admin.dao;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lalal.modules.base.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 车站实体（只读，用于管理后台查询）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_station")
public class StationDO extends BaseDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 车站编码
     */
    private String code;

    /**
     * 车站名称
     */
    private String name;

    /**
     * 拼音
     */
    private String spell;

    /**
     * 地区编号
     */
    private String region;

    /**
     * 地区名称
     */
    private String regionName;
}
