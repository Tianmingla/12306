package com.lalal.modules.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SeatLocation {
    private int carIndex;
    private String carNo;
    private int seatIndex;
    private String seatNo;

}