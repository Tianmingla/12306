package com.lalal.modules.admin.dao;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lalal.modules.base.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 列车实体（只读，用于管理后台查询）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_train")
public class TrainDO extends BaseDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 列车车次
     */
    private String trainNumber;

    /**
     * 列车类型 0：高铁 1：动车 2：普通车
     */
    private Integer trainType;

    /**
     * 列车标签
     */
    private String trainTag;

    /**
     * 列车品牌类型
     */
    private String trainBrand;

    /**
     * 销售状态 0：可售 1：不可售 2：未知
     */
    private Integer saleStatus;
}
