package com.lalal.modules.enumType.train;

import java.util.HashMap;
import java.util.Map;

/**
 * 座位类型枚举
 */
public enum SeatType {
    // 高铁/动车座位类型
    SECOND_CLASS(0, "二等座"),
    FIRST_CLASS(1, "一等座"),
    BUSINESS(2, "商务座"),

    // 普通列车座位类型
    SOFT_SLEEPER(3, "软卧"),
    HARD_SLEEPER(4, "硬卧"),
    HARD_SEAT(5, "硬座"),
    NO_SEAT(6, "无座"),

    // 扩展座位类型（普通列车详细分类）
    SOFT_SEAT(7, "软座"),
    HARD_SLEEPER_UPPER(8, "硬卧上铺"),
    HARD_SLEEPER_MIDDLE(9, "硬卧中铺"),
    HARD_SLEEPER_LOWER(10, "硬卧下铺"),
    SOFT_SLEEPER_UPPER(11, "软卧上铺"),
    SOFT_SLEEPER_LOWER(12, "软卧下铺"),
    DELUXE_SLEEPER_UPPER(13, "高级软卧上铺"),
    DELUXE_SLEEPER_LOWER(14, "高级软卧下铺");

    private final int code;
    private final String desc;
    private static final Map<Integer, SeatType> CODE_MAP = new HashMap<>();
    private static final Map<String, SeatType> DESC_MAP = new HashMap<>();
    static{
        for(SeatType seatType:values()){
            CODE_MAP.put(seatType.getCode(),seatType);
            DESC_MAP.put(seatType.getDesc(),seatType);
        }
    }
    SeatType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() { return code; }
    public String getDesc() { return desc; }
    public static String getDescByCode(Integer code){return CODE_MAP.get(code).getDesc();}
    public static SeatType findByCode(Integer code){return CODE_MAP.get(code);}
    public static SeatType findByDesc(String desc) {
        return DESC_MAP.get(desc);
    }

    /**
     * 判断是否为卧铺类型
     */
    public boolean isSleeper() {
        return this == SOFT_SLEEPER || this == HARD_SLEEPER ||
               this == HARD_SLEEPER_UPPER || this == HARD_SLEEPER_MIDDLE || this == HARD_SLEEPER_LOWER ||
               this == SOFT_SLEEPER_UPPER || this == SOFT_SLEEPER_LOWER ||
               this == DELUXE_SLEEPER_UPPER || this == DELUXE_SLEEPER_LOWER;
    }

    /**
     * 判断是否为硬席（硬座、硬卧）
     */
    public boolean isHardSeat() {
        return this == HARD_SEAT || this == HARD_SLEEPER ||
               this == HARD_SLEEPER_UPPER || this == HARD_SLEEPER_MIDDLE || this == HARD_SLEEPER_LOWER;
    }

    /**
     * 判断是否有空调
     */
    public boolean hasAirConditioning() {
        // 高铁动车商务座、一等座、二等座默认有空调
        // 普通列车软卧、软座有空调
        return this == SECOND_CLASS || this == FIRST_CLASS || this == BUSINESS ||
               this == SOFT_SLEEPER || this == SOFT_SEAT ||
               this == SOFT_SLEEPER_UPPER || this == SOFT_SLEEPER_LOWER ||
               this == DELUXE_SLEEPER_UPPER || this == DELUXE_SLEEPER_LOWER;
    }

    /**
     * 获取详细的卧铺类型（上/中/下铺）
     * 如果是通用硬卧/软卧，默认返回下铺
     */
    public SeatType getDetailedSleeperType() {
        switch (this) {
            case HARD_SLEEPER:
                return HARD_SLEEPER_LOWER; // 默认下铺
            case SOFT_SLEEPER:
                return SOFT_SLEEPER_LOWER; // 默认下铺
            default:
                return this;
        }
    }
}