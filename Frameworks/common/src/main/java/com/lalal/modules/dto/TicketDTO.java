package com.lalal.modules.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TicketDTO {
    private String account;
    private String trainNum;
    private String startStation;
    private String endStation;
    private String date;
    private List<TicketItem> items;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TicketItem {
        private long passengerId;
        private String seatNum;
        private Integer seatType;
        private String carriageNum;
    }
}
