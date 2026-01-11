package com.lalal.modules.core.selector;

import com.lalal.modules.enumType.train.SeatType;
import com.lalal.modules.model.Train;

import java.util.List;

// 3. 车厢选择器接口
public interface CarriageSelector {
    List<Integer> selectCandidates(Train train, SeatType type, int count);
}