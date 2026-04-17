package com.lalal.modules.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 票价计算请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FareCalculationRequestDTO {

    /**
     * 列车ID
     */
    private Long trainId;

    /**
     * 车次号
     */
    private String trainNumber;

    /**
     * 出发站名称
     */
    private String departureStation;

    /**
     * 到达站名称
     */
    private String arrivalStation;

    /**
     * 座位类型 (SeatType code)
     */
    private Integer seatType;

    /**
     * 旅客类型 (PassengerTypeEnum code)
     * 0-成人, 1-儿童, 2-学生, 3-伤残军人
     */
    private Integer passengerType;

    /**
     * 车次品牌 (G/C/D/Z/T/K 等)
     */
    private String trainBrand;

    /**
     * 是否春运期间
     */
    private Boolean isPeakSeason;

    /**
     * 乘客ID（用于关联）
     */
    private Long passengerId;
}
