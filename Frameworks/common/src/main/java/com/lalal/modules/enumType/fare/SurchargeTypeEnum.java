package com.lalal.modules.enumType.fare;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * 列车票价上浮类型枚举
 */
@Getter
public enum SurchargeTypeEnum {
    NORMAL(0, "普通车", 1.0),
    NEW_AC_50(1, "新型空调车", 1.5),
    NEW_AC_DISCOUNT_40(2, "新型空调车一档折扣", 1.4),
    NEW_AC_DISCOUNT_30(3, "新型空调车二档折扣", 1.3),
    DELUXE_SLEEPER_180(4, "高级软卧180%", 2.8),
    DELUXE_SLEEPER_208(5, "高级软卧208%(沈局/哈局)", 3.08);

    private final int code;
    private final String description;
    private final double multiplier;

    private static final Map<Integer, SurchargeTypeEnum> CODE_MAP = new HashMap<>();

    static {
        for (SurchargeTypeEnum type : values()) {
            CODE_MAP.put(type.code, type);
        }
    }

    SurchargeTypeEnum(int code, String description, double multiplier) {
        this.code = code;
        this.description = description;
        this.multiplier = multiplier;
    }

    public static SurchargeTypeEnum fromCode(int code) {
        return CODE_MAP.getOrDefault(code, NORMAL);
    }

    /**
     * 根据车次品牌判断上浮类型
     * @param trainBrand 车次品牌 (G/C/D/Z/T/K 等)
     * @return 上浮类型
     */
    public static SurchargeTypeEnum fromTrainBrand(String trainBrand) {
        if (trainBrand == null || trainBrand.isEmpty()) {
            return NORMAL;
        }
        char brand = trainBrand.toUpperCase().charAt(0);
        switch (brand) {
            case 'G':
            case 'C':
            case 'D':
                // 高铁、城际、动车默认为新空调车
                return NEW_AC_50;
            case 'Z':
            case 'T':
            case 'K':
                // 直达特快、特快、快速默认为新空调车
                return NEW_AC_50;
            default:
                // 普通列车
                return NORMAL;
        }
    }
}
