package com.lalal.modules.enumType.train;

public enum TrainCategory {
    HIGH_SPEED(0, "高铁"),
    EMU(1, "动车"),
    CONVENTIONAL(2, "普通车");

    private final int code;
    private final String desc;

    TrainCategory(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() { return code; }
    public String getDesc() { return desc; }

    public static TrainCategory fromCode(int code) {
        for (TrainCategory t : values()) {
            if (t.code == code) return t;
        }
        throw new IllegalArgumentException("Invalid train category code: " + code);
    }
}