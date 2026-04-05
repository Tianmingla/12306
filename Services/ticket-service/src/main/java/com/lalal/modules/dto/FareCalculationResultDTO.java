package com.lalal.modules.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 票价计算结果DTO
 */
@Data
public class FareCalculationResultDTO {

    /**
     * 列车ID
     */
    private Long trainId;

    /**
     * 出发站名称
     */
    private String departureStation;

    /**
     * 到达站名称
     */
    private String arrivalStation;

    /**
     * 里程(公里)
     */
    private Integer distance;

    /**
     * 座位类型
     */
    private Integer seatType;

    // ==================== 各分项票价 ====================

    /**
     * 客票票价（硬座/软座/二等座等）
     */
    private BigDecimal seatFare;

    /**
     * 加快票价（普快/快速）
     */
    private BigDecimal expressFare;

    /**
     * 卧铺票价（硬卧/软卧）
     */
    private BigDecimal sleeperFare;

    /**
     * 空调票价
     */
    private BigDecimal acFare;

    /**
     * 保险费（按硬座基本票价2%计算）
     */
    private BigDecimal insuranceFare;

    // ==================== 附加费 ====================

    /**
     * 客票发展金
     * 票价≤5元收0.5元，>5元收1元
     */
    private BigDecimal ticketDevFund;

    /**
     * 候车室空调费
     * 硬席旅客乘车>200km收1元
     */
    private BigDecimal waitingRoomAcFee;

    /**
     * 卧铺订票费
     * 购买卧铺票收10元
     */
    private BigDecimal sleeperBookingFee;

    // ==================== 合计 ====================

    /**
     * 基本票价 = 客票票价 + 附加票票价（加快+卧铺+空调）
     */
    private BigDecimal baseFare;

    /**
     * 旅客票价 = 基本票价 + 保险费
     */
    private BigDecimal passengerFare;

    /**
     * 联合票价 = 旅客票价 + 附加费（最终票价）
     */
    private BigDecimal totalFare;

    /**
     * 乘客ID（用于关联）
     */
    private Long passengerId;
}
