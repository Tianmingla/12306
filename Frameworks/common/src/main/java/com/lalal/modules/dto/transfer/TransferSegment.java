package com.lalal.modules.dto.transfer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 换乘路段（一个车次的一段区间）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferSegment implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 车次号
     */
    private String trainNumber;

    /**
     * 列车类型：高铁/动车/普通
     */
    private int trainType;

    /**
     * 出发站
     */
    private String departureStation;

    /**
     * 出发时间 (HH:mm)
     */
    private String departureTime;

    /**
     * 到达站
     */
    private String arrivalStation;

    /**
     * 到达时间 (HH:mm)
     */
    private String arrivalTime;

    /**
     * 历时（分钟）
     */
    private int durationMinutes;

    /**
     * 票价Map (座位类型 -> 票价)
     */
    private Map<String, BigDecimal> priceMap;

    /**
     * 余票Map (座位类型 -> 余票数)
     */
    private Map<String, Integer> remainingMap;

    /**
     * 座位类型列表
     */
    private List<Integer> seatTypes;
}
