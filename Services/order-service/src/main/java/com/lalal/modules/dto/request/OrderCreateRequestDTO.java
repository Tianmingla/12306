package com.lalal.modules.dto.request;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Data
public class OrderCreateRequestDTO {
    private String trainNumber;
    private String startStation;
    private String endStation;
    private String username;
    private Date run_date;
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
