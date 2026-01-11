package com.lalal.modules.core.seat;

import com.lalal.modules.model.CarLayout;

public class HardSeatParser implements Parser {
    @Override
    public int index(String seatNumber) {
        if (seatNumber == null || seatNumber.isEmpty()) {
            throw new IllegalArgumentException("Hard seat number is empty");
        }
        // 移除非数字字符（防万一）
        String digits = seatNumber.replaceAll("\\D+", "");
        if (digits.isEmpty()) {
            throw new IllegalArgumentException("No digits in hard seat: " + seatNumber);
        }
        return Integer.parseInt(digits);
    }

    @Override
    public CarLayout carLayout() {
        return new CarLayout(15,5);
    }
}