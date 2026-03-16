package com.lalal.modules.model;

import com.lalal.modules.enumType.train.SeatType;
import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Data
@Builder
public class CarLayout {
    private int rows;           // 排数
    private int cols;           // 每排座位数
    private SeatType seatType;  // 车厢类型

    // 座位后缀映射 (A,B,C,D,F)
    private static final String[] COL_SUFFIX = {"A", "B", "C", "D", "F"};

    // ========== 不同车厢类型的座位属性配置 ==========

    // 靠窗座位的列索引 (0-based)
    private static final Map<SeatType, Set<Integer>> WINDOW_COL_MAP = new HashMap<>();
    // 过道座位的列索引
    private static final Map<SeatType, Set<Integer>> AISLE_COL_MAP = new HashMap<>();
    // 硬卧/软卧的铺位类型 (下/中/上)
    private static final Map<SeatType, Map<Integer, String>> BERTH_TYPE_MAP = new HashMap<>();

    static {
        // 二等座 3+2 布局: A B C | 过道 | D F (A/F靠窗, C/D过道)
        WINDOW_COL_MAP.put(SeatType.SECOND_CLASS, Set.of(0, 4));
        AISLE_COL_MAP.put(SeatType.SECOND_CLASS, Set.of(2, 3));

        // 一等座 2+2 布局: A C | 过道 | D F (A/F靠窗, C/D过道)
        WINDOW_COL_MAP.put(SeatType.FIRST_CLASS, Set.of(0, 3));
        AISLE_COL_MAP.put(SeatType.FIRST_CLASS, Set.of(1, 2));

        // 商务座 2+1 或 1+1 布局 (假设 2+1: A C | 过道 | F)
        WINDOW_COL_MAP.put(SeatType.BUSINESS, Set.of(0, 2));
        AISLE_COL_MAP.put(SeatType.BUSINESS, Set.of(1));

        // 硬座 3+2 布局 (同二等座)
        WINDOW_COL_MAP.put(SeatType.HARD_SEAT, Set.of(0, 4));
        AISLE_COL_MAP.put(SeatType.HARD_SEAT, Set.of(2, 3));

        // 软座 2+2 布局 (同一等座)
        WINDOW_COL_MAP.put(SeatType.SOFT_SLEEPER, Set.of(0, 3));
        AISLE_COL_MAP.put(SeatType.SOFT_SLEEPER, Set.of(1, 2));

        // 硬卧: 每格6人 (1-2下铺, 3-4中铺, 5-6上铺) - 按列索引判断
        // 假设布局: 1(下) 2(下) | 3(中) 4(中) | 5(上) 6(上)
        Map<Integer, String> hardBerth = new HashMap<>();
        hardBerth.put(0, "LOWER"); hardBerth.put(1, "LOWER");
        hardBerth.put(2, "MIDDLE"); hardBerth.put(3, "MIDDLE");
        hardBerth.put(4, "UPPER"); hardBerth.put(5, "UPPER");
        BERTH_TYPE_MAP.put(SeatType.HARD_SLEEPER, hardBerth);

        // 软卧: 每格4人 (1-2下铺, 3-4上铺)
        Map<Integer, String> softBerth = new HashMap<>();
        softBerth.put(0, "LOWER"); softBerth.put(1, "LOWER");
        softBerth.put(2, "UPPER"); softBerth.put(3, "UPPER");
        BERTH_TYPE_MAP.put(SeatType.SOFT_SLEEPER, softBerth);
    }

    public int getTotalSeats() {
        return rows * cols;
    }

    /**
     * 根据索引获取座位号 (如 "5A", "12F")
     */
    public String getSeatNo(int index) {
        if (isSleeper()) {
            // 卧铺车厢直接返回数字 (如 "1", "2", "17")
            return String.valueOf(index + 1);
        }
        int row = (index / cols) + 1;
        int colIdx = index % cols;
        return row + COL_SUFFIX[colIdx];
    }

    /**
     * 判断座位是否靠窗
     */
    public boolean isWindowSeat(int index) {
        int colIdx = index % cols;
        Set<Integer> windowCols = WINDOW_COL_MAP.get(seatType);
        return windowCols != null && windowCols.contains(colIdx);
    }

    /**
     * 判断座位是否靠过道
     */
    public boolean isAisleSeat(int index) {
        int colIdx = index % cols;
        Set<Integer> aisleCols = AISLE_COL_MAP.get(seatType);
        return aisleCols != null && aisleCols.contains(colIdx);
    }

    /**
     * 获取铺位类型 (仅卧铺有效)
     */
    public String getBerthType(int index) {
        if (!isSleeper()) {
            return null;
        }
        int colIdx = index % cols;
        Map<Integer, String> berthMap = BERTH_TYPE_MAP.get(seatType);
        return berthMap != null ? berthMap.get(colIdx) : null;
    }

    /**
     * 判断是否为卧铺
     */
    public boolean isSleeper() {
        return seatType == SeatType.HARD_SLEEPER || seatType == SeatType.SOFT_SLEEPER;
    }

    /**
     * 判断两个座位是否相邻 (同一排且列相邻)
     */
    public boolean isAdjacent(int index1, int index2) {
        int row1 = index1 / cols;
        int row2 = index2 / cols;
        int col1 = index1 % cols;
        int col2 = index2 % cols;

        // 必须同一排
        if (row1 != row2) {
            return false;
        }
        // 列相邻 (考虑过道情况)
        return Math.abs(col1 - col2) == 1;
    }

    /**
     * 判断两个座位是否在同一包厢 (仅卧铺)
     */
    public boolean isInSameCompartment(int index1, int index2) {
        if (!isSleeper()) {
            return false;
        }
        // 硬卧每格6人, 软卧每格4人
        int compartmentSize = (seatType == SeatType.HARD_SLEEPER) ? 6 : 4;
        return (index1 / compartmentSize) == (index2 / compartmentSize);
    }
}