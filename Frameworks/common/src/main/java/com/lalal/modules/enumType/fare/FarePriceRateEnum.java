package com.lalal.modules.enumType.fare;

import lombok.Getter;

/**
 * 票价率枚举 - 基于硬座票价率的倍率
 * 基础票价率: 0.05861 元/人公里
 */
@Getter
public enum FarePriceRateEnum {
    // 客票票价
    HARD_SEAT(1.0, "硬座客票", 100),
    SOFT_SEAT(2.0, "软座客票", 200),

    // 加快票
    EXPRESS(0.2, "普快", 20),
    FAST_EXPRESS(0.4, "快速", 40),

    // 硬卧票（开放式）
    HARD_SLEEPER_UPPER(1.1, "硬卧上铺", 110),
    HARD_SLEEPER_MIDDLE(1.2, "硬卧中铺", 120),
    HARD_SLEEPER_LOWER(1.3, "硬卧下铺", 130),

    // 软卧票
    SOFT_SLEEPER_UPPER(1.75, "软卧上铺", 175),
    SOFT_SLEEPER_LOWER(1.95, "软卧下铺", 195),

    // 高级软卧票
    DELUXE_SLEEPER_UPPER(2.1, "高级软卧上铺", 210),
    DELUXE_SLEEPER_LOWER(2.3, "高级软卧下铺", 230),

    // 空调票
    AIR_CONDITIONING(0.25, "空调票", 25);

    /**
     * 倍率（相对于硬座基础票价率）
     */
    private final double rateMultiplier;

    /**
     * 描述
     */
    private final String description;

    /**
     * 比例百分比
     */
    private final int ratioPercent;

    /**
     * 硬座基础票价率：元/(人公里)
     */
    public static final double BASE_RATE = 0.05861;

    FarePriceRateEnum(double rateMultiplier, String description, int ratioPercent) {
        this.rateMultiplier = rateMultiplier;
        this.description = description;
        this.ratioPercent = ratioPercent;
    }

    /**
     * 获取实际票价率
     * @return 实际票价率 元/(人公里)
     */
    public double getActualRate() {
        return BASE_RATE * rateMultiplier;
    }
}
