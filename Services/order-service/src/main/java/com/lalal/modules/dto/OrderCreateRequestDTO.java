package com.lalal.modules.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class OrderCreateRequestDTO {
    private String trainNumber;
    private String startStation;
    private String endStation;
    private List<OrderItemRequestDTO> items;

    @Data
    public static class OrderItemRequestDTO {
        private Long passengerId;
        private String carriageNumber;
        private String seatNumber;
        private Integer seatType;
        private BigDecimal amount;
        private String realName;
        private String idCard;
    }
}
