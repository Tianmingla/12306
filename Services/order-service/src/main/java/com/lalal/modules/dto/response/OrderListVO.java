package com.lalal.modules.dto.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

/**
 * 订单列表VO
 */
@Data
public class OrderListVO {
    private String orderSn;
    private String trainNumber;
    private String startStation;
    private String endStation;
    private LocalDate runDate;
    private BigDecimal totalAmount;
    private Integer status;
    private String statusText;
    private Integer passengerCount;
}
