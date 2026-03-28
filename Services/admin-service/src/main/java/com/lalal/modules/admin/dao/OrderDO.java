package com.lalal.modules.admin.dao;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lalal.modules.base.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 订单实体（只读，用于管理后台查询）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_order")
public class OrderDO extends BaseDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 订单编号
     */
    private String orderSn;

    /**
     * 用户名
     */
    private String username;

    /**
     * 车次
     */
    private String trainNumber;

    /**
     * 出发站
     */
    private String startStation;

    /**
     * 到达站
     */
    private String endStation;

    /**
     * 乘车日期
     */
    private Date runDate;

    /**
     * 订单总金额
     */
    private BigDecimal totalAmount;

    /**
     * 订单状态：0-待支付, 1-已支付, 2-已取消, 3-已退票
     */
    private Integer status;
}
