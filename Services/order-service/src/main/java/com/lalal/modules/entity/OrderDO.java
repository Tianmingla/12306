package com.lalal.modules.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lalal.modules.base.BaseDO;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

@Data
@TableName("t_order")
public class OrderDO extends BaseDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderSn;
    private String username;
    private String trainNumber;
    private LocalDateTime payTime;
    private String startStation;
    private String endStation;
    private LocalDate runDate;
    private BigDecimal totalAmount;
    private Integer status; // 0: 待支付, 1: 已支付, 2: 已取消, 3: 已退票
}
