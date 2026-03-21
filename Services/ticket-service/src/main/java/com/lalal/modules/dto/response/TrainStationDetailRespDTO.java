package com.lalal.modules.dto.response;


import lombok.Data;
import java.util.Date;

@Data
public class TrainStationDetailRespDTO {
    private String stationName;
    private Date arrivalTime;
    private Date departureTime;
    private Integer stopoverTime;
}
