package com.lalal.modules.graph;

import lombok.Getter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * 乘车边：表示乘坐列车从 A 站到 B 站
 */
@Getter
public class TrainEdge extends TransitEdge implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 车次号
     */
    private final String trainNumber;

    /**
     * 列车类型
     */
    private final int trainType;

    /**
     * 出发站
     */
    private final String departureStation;

    /**
     * 到达站
     */
    private final String arrivalStation;

    /**
     * 出发时间
     */
    private final LocalDateTime departureTime;

    /**
     * 到达时间
     */
    private final LocalDateTime arrivalTime;

    /**
     * 可用座位类型列表
     */
    private final List<Integer> seatTypes;

    /**
     * 座位类型到票价的映射
     */
    private final List<SeatPrice> seatPrices;

    /**
     * 座位类型到余票数的映射
     */
    private final List<SeatRemaining> seatRemainings;

    /**
     * 座位票价
     */
    public record SeatPrice(int seatType, BigDecimal price) implements Serializable {}

    /**
     * 座位余票
     */
    public record SeatRemaining(int seatType, int remaining) implements Serializable {}

    public TrainEdge(StationTimeNode from,
                     StationTimeNode to,
                     String trainNumber,
                     int trainType,
                     String departureStation,
                     String arrivalStation,
                     LocalDateTime departureTime,
                     LocalDateTime arrivalTime,
                     List<Integer> seatTypes,
                     List<SeatPrice> seatPrices,
                     List<SeatRemaining> seatRemainings) {
        super(from.getKey(), to.getKey(),
              (int) java.time.Duration.between(departureTime, arrivalTime).toMinutes(),
              0, EdgeType.TRAIN);
        this.trainNumber = trainNumber;
        this.trainType = trainType;
        this.departureStation = departureStation;
        this.arrivalStation = arrivalStation;
        this.departureTime = departureTime;
        this.arrivalTime = arrivalTime;
        this.seatTypes = seatTypes;
        this.seatPrices = seatPrices;
        this.seatRemainings = seatRemainings;
    }

    /**
     * 检查是否有某座位类型的余票
     */
    public boolean hasRemaining(int seatType) {
        return seatRemainings.stream()
                .anyMatch(sr -> sr.seatType() == seatType && sr.remaining() > 0);
    }

    /**
     * 获取某座位类型的票价
     */
    public double getPrice(int seatType) {
        return seatPrices.stream()
                .filter(sp -> sp.seatType() == seatType)
                .mapToDouble(sp-> sp.price.doubleValue())
                .findFirst()
                .orElse(0);
    }

    /**
     * 获取最低票价
     */
    public double getMinPrice() {
        return seatPrices.stream()
                .mapToDouble(sp-> sp.price.doubleValue())
                .min()
                .orElse(0);
    }

    @Override
    public String toString() {
        return String.format("TrainEdge[%s: %s->%s (%s-%s, %dmin)]",
                trainNumber, departureStation, arrivalStation,
                departureTime.toLocalTime(), arrivalTime.toLocalTime(), durationMinutes);
    }
}
