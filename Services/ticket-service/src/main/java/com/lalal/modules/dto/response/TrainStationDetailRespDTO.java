package com.lalal.modules.dto.response;


import lombok.Data;

import java.time.LocalTime;
import java.util.Date;

@Data
public class TrainStationDetailRespDTO {
    private String stationName;
    private LocalTime arrivalTime;
    private LocalTime departureTime;
    private Integer stopoverTime;
}
