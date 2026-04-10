package com.lalal.modules.dto.request;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

@Data
public class OrderCreateRequestDTO {
    private String trainNumber;
    private String startStation;
    private String endStation;
    private String username;
    /** 与 ticket-service Feign 字段名 runDate 对齐 */
    private LocalDate runDate;
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
