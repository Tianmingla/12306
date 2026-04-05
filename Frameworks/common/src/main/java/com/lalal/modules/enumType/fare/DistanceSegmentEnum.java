package com.lalal.modules.enumType.fare;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * 里程区段枚举
 * 用于计算票价时的里程区段划分
 */
@Getter
public enum DistanceSegmentEnum {
    SEGMENT_1_200(1, 200, 10),
    SEGMENT_201_400(201, 400, 20),
    SEGMENT_401_700(401, 700, 30),
    SEGMENT_701_1100(701, 1100, 40),
    SEGMENT_1101_1600(1101, 1600, 50),
    SEGMENT_1601_2200(1601, 2200, 60),
    SEGMENT_2201_2900(2201, 2900, 70),
    SEGMENT_2901_3700(2901, 3700, 80),
    SEGMENT_3701_4600(3701, 4600, 90),
    SEGMENT_4601_PLUS(4601, Integer.MAX_VALUE, 100);

    /**
     * 区段起始里程（公里）
     */
    private final int minDistance;

    /**
     * 区段结束里程（公里）
     */
    private final int maxDistance;

    /**
     * 每区段里程（公里）
     */
    private final int segmentUnit;

    private static final Map<Integer, DistanceSegmentEnum> DISTANCE_MAP = new HashMap<>();

    static {
        for (DistanceSegmentEnum segment : values()) {
            DISTANCE_MAP.put(segment.minDistance, segment);
        }
    }

    DistanceSegmentEnum(int minDistance, int maxDistance, int segmentUnit) {
        this.minDistance = minDistance;
        this.maxDistance = maxDistance;
        this.segmentUnit = segmentUnit;
    }

    /**
     * 根据里程查找对应的区段
     * @param distance 里程（公里）
     * @return 里程区段枚举
     */
    public static DistanceSegmentEnum findByDistance(int distance) {
        for (DistanceSegmentEnum segment : values()) {
            if (distance >= segment.minDistance && distance <= segment.maxDistance) {
                return segment;
            }
        }
        return SEGMENT_4601_PLUS;
    }

    /**
     * 计算区段中间里程
     * 同一里程区段核收同一票价，按中间里程计算
     * @param distance 实际里程
     * @return 中间里程
     */
    public static int calculateMiddleDistance(int distance) {
        if (distance <= 0) {
            return 0;
        }

        DistanceSegmentEnum segment = findByDistance(distance);

        // 区段数 = (实际里程 - 区段起点) / 每区段里程
        int segmentCount = (distance - segment.minDistance) / segment.segmentUnit;

        // 中间里程 = 区段起点 + 区段数 × 每区段里程 + 每区段里程 / 2
        int middleDistance = segment.minDistance + segmentCount * segment.segmentUnit + segment.segmentUnit / 2;

        return middleDistance;
    }
}
