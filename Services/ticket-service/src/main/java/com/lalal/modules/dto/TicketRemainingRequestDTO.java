package com.lalal.modules.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 余票查询请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketRemainingRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 车次ID（优先使用）
     */
    private Long trainId;

    /**
     * 车次号
     */
    private String trainNumber;

    /**
     * 乘车日期 yyyy-MM-dd
     */
    private String date;

    /**
     * 座位类型
     */
    private Integer seatType;

    /**
     * 出发站
     */
    private String departureStation;

    /**
     * 到达站
     */
    private String arrivalStation;
}
