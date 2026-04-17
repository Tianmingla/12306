package com.lalal.modules.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * 余票查询结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketRemainingResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 车次ID
     */
    private Long trainId;

    /**
     * 车次号
     */
    private String trainNumber;

    /**
     * 出发站
     */
    private String departureStation;

    /**
     * 到达站
     */
    private String arrivalStation;

    /**
     * 座位类型
     */
    private Integer seatType;

    /**
     * 余票数量，null表示无票或无法计算
     */
    private Integer remainingTickets;

    /**
     * 是否有余票
     */
    private boolean hasAvailable;

    /**
     * 各座位类型余票（批量查询时返回）
     */
    private Map<Integer, Integer> remainingByType;

    /**
     * 座位总数
     */
    private Integer totalSeats;

    /**
     * 已售座位数
     */
    private Integer soldSeats;
}
