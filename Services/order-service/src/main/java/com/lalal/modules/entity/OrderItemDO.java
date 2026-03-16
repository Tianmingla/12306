package com.lalal.modules.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lalal.modules.base.BaseDO;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("t_order_item")
public class OrderItemDO extends BaseDO {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long orderId;
    private String orderSn;
    private Long userId;
    private String username;
    private String idCard;
    private String carriageNumber;
    private String seatNumber;
    private Integer seatType;
    private BigDecimal amount;
    private String realName;
}
