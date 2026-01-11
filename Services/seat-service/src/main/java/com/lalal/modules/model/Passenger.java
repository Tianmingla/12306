package com.lalal.modules.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class Passenger {
    private Long id;
    private String seatType;
    /**
     * 偏向座位号
     */
    private String seatPreference;
}