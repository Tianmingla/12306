package com.lalal.modules.model;

public class CarLayout {
    private int rows;
    private int cols; // 例如二等座 3+2 布局，cols=5
    // 简单映射，实际可用 Map 配置
    private static final String[] COL_SUFFIX = {"A", "B", "C", "D", "F"};

    public CarLayout(int rows, int cols) {
        this.rows = rows;
        this.cols = cols;
    }

    public int getTotalSeats() {
        return rows * cols;
    }

    public String getSeatNo(int index) {
        int row = (index / cols) + 1;
        int colIdx = index % cols;
        return row + COL_SUFFIX[colIdx];
    }
}