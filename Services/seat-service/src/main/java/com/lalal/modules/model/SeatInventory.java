package com.lalal.modules.model;

import com.lalal.modules.core.tool.IntervalCombiner;
import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
@Data
public class SeatInventory {
    private final Train train;
    // Key: carriageIndex, Value: 该车厢合并区间后的最终 Mask
    private final Map<Integer, BooleanMask> carriageMasks;
    private final String startStation;
    private final String endStation;

    public SeatInventory(Train train,String startStation,String endStation) {
        this.train=train;
        this.carriageMasks = new HashMap<>();
        this.startStation=startStation;
        this.endStation=endStation;
    }

    // 初始化时加载数据
    public void loadCarriageMask(int carIndex,List<BooleanMask> segmentMasks) {
        // 立即合并所有区间的 Mask，计算出当前“最大占用情况”
        int start= train.stationIndex(startStation);
        int end=train.stationIndex(endStation)-1;
        BooleanMask finalMask = IntervalCombiner.combine(segmentMasks,start,end);
        carriageMasks.put(carIndex, finalMask);
    }

    public BooleanMask getMask(int carIndex) {
        return carriageMasks.get(carIndex);
    }
}