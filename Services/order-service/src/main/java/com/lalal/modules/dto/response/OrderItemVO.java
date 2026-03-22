package com.lalal.modules.dto.response;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderItemVO {
    private Long id;
    private Long passengerId;
    private String passengerName;
    private String idCardMasked;
    private String carriageNumber;
    private String seatNumber;
    private Integer seatType;
    private BigDecimal amount;
}
