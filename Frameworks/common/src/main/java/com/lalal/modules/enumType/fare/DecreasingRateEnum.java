package com.lalal.modules.enumType.fare;

import lombok.Getter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 递远递减率枚举
 * 旅客票价从201km起实行递远递减
 */
@Getter
public enum DecreasingRateEnum {
    /**
     * 1-200km，无递减
     */
    RANGE_1_200(1, 200, 0.0, 0.05861, 11.722, 11.722),
    /**
     * 201-500km，递减10%
     */
    RANGE_201_500(201, 500, 0.1, 0.052749, 15.8247, 27.5467),
    /**
     * 501-1000km，递减20%
     */
    RANGE_501_1000(501, 1000, 0.2, 0.046888, 23.444, 50.9907),
    /**
     * 1001-1500km，递减30%
     */
    RANGE_1001_1500(1001, 1500, 0.3, 0.041027, 20.5135, 71.5042),
    /**
     * 1501-2500km，递减40%
     */
    RANGE_1501_2500(1501, 2500, 0.4, 0.035166, 35.166, 106.6702),
    /**
     * 2501km以上，递减50%
     */
    RANGE_2501_PLUS(2501, Integer.MAX_VALUE, 0.5, 0.029305, null, null);

    /**
     * 区段起始里程（公里）
     */
    private final int minDistance;

    /**
     * 区段结束里程（公里）
     */
    private final int maxDistance;

    /**
     * 递减率
     */
    private final double decreasingRate;

    /**
     * 实际票价率 元/(人公里)
     */
    private final double actualRate;

    /**
     * 各区段全程票价（元）
     */
    private final BigDecimal segmentFullFare;

    /**
     * 区段累计票价（元）
     */
    private final BigDecimal cumulativeFare;

    DecreasingRateEnum(int minDistance, int maxDistance, double decreasingRate,
                       double actualRate, Double segmentFullFare, Double cumulativeFare) {
        this.minDistance = minDistance;
        this.maxDistance = maxDistance;
        this.decreasingRate = decreasingRate;
        this.actualRate = actualRate;
        this.segmentFullFare = segmentFullFare != null ? BigDecimal.valueOf(segmentFullFare) : null;
        this.cumulativeFare = cumulativeFare != null ? BigDecimal.valueOf(cumulativeFare) : null;
    }

    /**
     * 获取所有区段（按起始里程升序）
     */
    public static List<DecreasingRateEnum> getSortedSegments() {
        List<DecreasingRateEnum> list = new ArrayList<>();
        for (DecreasingRateEnum rate : values()) {
            list.add(rate);
        }
        Collections.sort(list, Comparator.comparingInt(DecreasingRateEnum::getMinDistance));
        return list;
    }

    /**
     * 应用递远递减计算硬座基础票价
     * @param distance 里程（公里）
     * @return 硬座基础票价
     */
    public static BigDecimal calculateBaseFare(int distance) {
        if (distance <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal totalFare = BigDecimal.ZERO;
        int remainingDistance = distance;

        for (DecreasingRateEnum rate : getSortedSegments()) {
            if (remainingDistance <= 0) {
                break;
            }

            int segmentStart = rate.minDistance;
            int segmentEnd = rate.maxDistance;

            // 判断是否进入该区段
            if (distance >= segmentStart) {
                // 计算该区段内实际行驶里程
                int actualSegmentEnd = Math.min(distance, segmentEnd);
                int segmentDistance;

                if (segmentStart == 1) {
                    // 第一个区段：1-200km
                    segmentDistance = Math.min(distance, segmentEnd);
                } else {
                    // 后续区段
                    segmentDistance = actualSegmentEnd - segmentStart + 1;
                }

                if (segmentDistance > 0) {
                    BigDecimal segmentFare = BigDecimal.valueOf(segmentDistance)
                            .multiply(BigDecimal.valueOf(rate.actualRate));
                    totalFare = totalFare.add(segmentFare);
                }
            }
        }

        return totalFare;
    }
}
