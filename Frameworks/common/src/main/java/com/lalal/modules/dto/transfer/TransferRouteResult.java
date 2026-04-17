package com.lalal.modules.dto.transfer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 换乘方案结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferRouteResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 方案ID
     */
    private String routeId;

    /**
     * 总耗时（分钟）
     */
    private int totalMinutes;

    /**
     * 总票价
     */
    private BigDecimal totalPrice;

    /**
     * 换乘次数
     */
    private int transferCount;

    /**
     * 出发时间
     */
    private String departureTime;

    /**
     * 到达时间
     */
    private String arrivalTime;

    /**
     * 出发站
     */
    private String fromStation;

    /**
     * 到达站
     */
    private String toStation;

    /**
     * 换乘路段列表
     */
    private List<TransferSegment> segments;

    /**
     * 综合评分（用于排序，越小越好）
     */
    private double score;

    /**
     * 是否有余票
     */
    private boolean hasAvailableSeats;
}
