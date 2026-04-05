package com.lalal.modules.enumType.fare;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * 旅客类型枚举 - 用于优惠票价计算
 */
@Getter
public enum PassengerTypeEnum {
    /**
     * 成人，全价
     */
    ADULT(0, "成人", 1.0, 1.0, 1.0, 1.0),

    /**
     * 儿童(身高1.1m-1.4m)：半价客票+加快票+空调票，全价卧铺票
     */
    CHILD(1, "儿童(1.1m-1.4m)", 0.5, 0.5, 0.5, 1.0),

    /**
     * 学生：半价硬座客票+加快票+空调票
     */
    STUDENT(2, "学生", 0.5, 0.5, 0.5, 1.0),

    /**
     * 伤残军人：半价（除附加费外）
     */
    DISABLED_MILITARY(3, "伤残军人", 0.5, 0.5, 0.5, 0.5);

    /**
     * 类型编码
     */
    private final int code;

    /**
     * 类型描述
     */
    private final String description;

    /**
     * 客票折扣
     */
    private final double seatDiscount;

    /**
     * 加快票折扣
     */
    private final double expressDiscount;

    /**
     * 空调票折扣
     */
    private final double acDiscount;

    /**
     * 卧铺票折扣
     */
    private final double sleeperDiscount;

    private static final Map<Integer, PassengerTypeEnum> CODE_MAP = new HashMap<>();

    static {
        for (PassengerTypeEnum type : values()) {
            CODE_MAP.put(type.code, type);
        }
    }

    PassengerTypeEnum(int code, String description,
                      double seatDiscount, double expressDiscount,
                      double acDiscount, double sleeperDiscount) {
        this.code = code;
        this.description = description;
        this.seatDiscount = seatDiscount;
        this.expressDiscount = expressDiscount;
        this.acDiscount = acDiscount;
        this.sleeperDiscount = sleeperDiscount;
    }

    public static PassengerTypeEnum fromCode(int code) {
        return CODE_MAP.getOrDefault(code, ADULT);
    }
}
