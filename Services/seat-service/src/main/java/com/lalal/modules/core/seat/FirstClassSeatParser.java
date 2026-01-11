package com.lalal.modules.core.seat;

import com.lalal.modules.model.CarLayout;

import java.util.Map;

public class FirstClassSeatParser implements Parser {
    private static final Map<String, String> MAPPING = Map.of(
            "A", "1", "C", "2", "D", "3", "F", "4"
    );

    @Override
    public int index(String seatNumber) {
        validate(seatNumber);
        String rowPart = seatNumber.substring(0, seatNumber.length() - 1);
        String letter = seatNumber.substring(seatNumber.length() - 1).toUpperCase();
        String col = MAPPING.get(letter);
        if (col == null) throw new IllegalArgumentException("Invalid seat: " + seatNumber);
        return Integer.parseInt(padRow(rowPart) + col);
    }

    @Override
    public CarLayout carLayout() {
        return new CarLayout(10,4);
    }

    private void validate(String s) {
        if (s == null || s.isEmpty() || !s.matches("\\d+[ACDF]")) {
            throw new IllegalArgumentException("Invalid first class seat format: " + s);
        }
    }

    private String padRow(String row) {
        return String.format("%02d", Integer.parseInt(row));
    }
}