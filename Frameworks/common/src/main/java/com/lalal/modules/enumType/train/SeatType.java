package com.lalal.modules.enumType.train;

import java.util.HashMap;
import java.util.Map;

public enum SeatType {
    SECOND_CLASS(0, "二等座"),
    FIRST_CLASS(1, "一等座"),
    BUSINESS(2, "商务座"),
    SOFT_SLEEPER(3, "软卧"),
    HARD_SLEEPER(4, "硬卧"),
    HARD_SEAT(5, "硬座"),
    NO_SEAT(6, "无座");

    private final int code;
    private final String desc;
    private static final Map<Integer, SeatType> CODE_MAP = new HashMap<>();
    static{
        for(SeatType seatType:values()){
            CODE_MAP.put(seatType.getCode(),seatType);
        }
    }
    SeatType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() { return code; }
    public String getDesc() { return desc; }
    public static String getDescByCode(Integer code){return CODE_MAP.get(code).getDesc();}
}