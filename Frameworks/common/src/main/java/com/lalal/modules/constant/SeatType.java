package com.lalal.modules.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SeatType {
    HARD_SEAT(0, "硬座"),
    SECOND_CLASS(1, "二等座"),
    FIRST_CLASS(2, "一等座"),
    BUSINESS_CLASS(3, "商务座"),
    SOFT_SEAT(4, "软座"),
    HARD_SLEEPER(5, "硬卧"),
    SOFT_SLEEPER(6, "软卧");

    private final int code;
    private final String description;

    public static SeatType fromCode(int code) {
        for (SeatType type : SeatType.values()) {
            if (type.code == code) {
                return type;
            }
        }
        return null;
    }
}
