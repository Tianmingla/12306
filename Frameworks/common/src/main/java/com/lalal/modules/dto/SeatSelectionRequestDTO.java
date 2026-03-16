package com.lalal.modules.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SeatSelectionRequestDTO {
    private String trainNum;
    private String startStation;
    private String endStation;
    private String date;
    private String account;
    private List<PassengerDTO> passengers;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PassengerDTO {
        private Long id;
        private String seatType;
    }
}
