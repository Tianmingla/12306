package com.lalal.modules.model;

import com.lalal.modules.enumType.train.SeatType;
import lombok.Data;

@Data
public class Carriage {
    private int index;      // 在列车中的逻辑索引 (0, 1, 2...)
    private String carNumber;  // 物理车厢号 (1号车, 5号车)
    private SeatType seatType;
    private CarLayout layout;

    // 辅助方法：判断索引是否靠窗/过道，委托给 Layout
}