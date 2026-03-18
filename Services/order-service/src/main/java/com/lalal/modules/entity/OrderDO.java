package com.lalal.modules.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_order")
public class OrderDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderSn;
    private Long userId;
    private String trainNumber;
    private String startStation;
    private String endStation;
    private LocalDateTime runDate;
    private BigDecimal totalAmount;
    private Integer status; // 0: 待支付, 1: 已支付, 2: 已取消, 3: 已退票
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
