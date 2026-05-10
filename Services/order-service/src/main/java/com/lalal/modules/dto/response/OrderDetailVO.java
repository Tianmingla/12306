package com.lalal.modules.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

@Data
public class OrderDetailVO {
    private String orderSn;
    private String username;
    private String trainNumber;
    private String startStation;
    private String endStation;
    private LocalDate runDate;
    private BigDecimal totalAmount;
    /** 0 待支付 1 已支付 2 已取消 3 已退票 4 已改签 */
    private Integer status;
    private String statusText;
    private String originalOrderSn;
    private List<OrderItemVO> items;
}
