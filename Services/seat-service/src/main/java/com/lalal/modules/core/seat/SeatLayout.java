package com.lalal.modules.core.seat;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 布局的数据描述
 * 索引和布局映射
 */
@Getter
public class SeatLayout {
    private final int seatsPerRow;
    private final String[] seatNames;
    private final boolean[][] adjacencyMatrix; // true if seat i and i+1 are adjacent

    public SeatLayout(int seatsPerRow, String[] seatNames, boolean[] adjacency) {
        this.seatsPerRow = seatsPerRow;
        this.seatNames = seatNames;
        this.adjacencyMatrix = new boolean[seatsPerRow][seatsPerRow];
        for (int i = 0; i < adjacency.length; i++) {
            if (adjacency[i]) {
                adjacencyMatrix[i][i + 1] = true;
                adjacencyMatrix[i + 1][i] = true;
            }
        }
    }

    //两个索引的是1-based
    public String getSeatNumber(int index) {
        int row = (index / seatsPerRow) + 1;
        String seatName = seatNames[index % seatsPerRow];
        return row + seatName;
    }
    public Integer getIndex(String seatNumber){
        int row=Integer.parseInt(String.valueOf(seatNumber.charAt(0)));
        int col= findIndex(seatNumber);
        if(col==-1){
            return null;
        }
        return seatsPerRow*row+col+1;
    }
    private int findIndex(String str) {
        for (int i = 0; i < seatNames.length; i++) {
            if (str.equals(seatNames[i])) {
                return i;

            }
        }
        return -1;
    }

    /**
     * 获取某一行的候选选座组（连续座）
     * @param row 行号（1-based）
     * @param count 需要的座位数
     * @return 索引数组列表
     */
    public List<int[]> getAdjacentGroups(int row, int count) {
        List<int[]> groups = new ArrayList<>();
        int startIdx = (row - 1) * seatsPerRow;

        if (count == 1) {
            for (int i = 0; i < seatsPerRow; i++) {
                groups.add(new int[]{startIdx + i});
            }
            return groups;
        }

        // 简单的滑动窗口检查连通性
        for (int i = 0; i <= seatsPerRow - count; i++) {
            boolean continuous = true;
            for (int j = i; j < i + count - 1; j++) {
                if (!adjacencyMatrix[j][j + 1]) {
                    continuous = false;
                    break;
                }
            }
            if (continuous) {
                int[] group = new int[count];
                for (int j = 0; j < count; j++) {
                    group[j] = startIdx + i + j;
                }
                groups.add(group);
            }
        }
        return groups;
    }

    // 二等座：A B C | D F (A-B, B-C, D-F 连通)
    public static final SeatLayout SECOND_CLASS = new SeatLayout(5,
            new String[]{"A", "B", "C", "D", "F"},
            new boolean[]{true, true, false, true});

    // 一等座：A C | D F (A-C, D-F 连通)
    public static final SeatLayout FIRST_CLASS = new SeatLayout(4,
            new String[]{"A", "C", "D", "F"},
            new boolean[]{true, false, true});

    // 商务座：A | C F (C-F 连通)
    public static final SeatLayout BUSINESS_CLASS = new SeatLayout(3,
            new String[]{"A", "C", "F"},
            new boolean[]{false, true});

    // 硬座：1 2 3 | 4 5 (1-2, 2-3, 4-5 连通)
    public static final SeatLayout HARD_SEAT = new SeatLayout(5,
            new String[]{"1", "2", "3", "4", "5"},
            new boolean[]{true, true, false, true});

    // 软座：A C | D F (A-C, D-F 连通) - 类似一等座
    public static final SeatLayout SOFT_SEAT = new SeatLayout(4,
            new String[]{"A", "C", "D", "F"},
            new boolean[]{true, false, true});

    // 硬卧：每隔6个是一个包间，内部全连通
    public static final SeatLayout HARD_SLEEPER = new SeatLayout(6,
            new String[]{"上", "中", "下", "上", "中", "下"},
            new boolean[]{true, true, true, true, true});

    // 软卧：每隔4个是一个包间，内部全连通
    public static final SeatLayout SOFT_SLEEPER = new SeatLayout(4,
            new String[]{"上", "下", "上", "下"},
            new boolean[]{true, true, true});
}
