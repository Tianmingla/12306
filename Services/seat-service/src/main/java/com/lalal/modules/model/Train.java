package com.lalal.modules.model;


import com.lalal.modules.enumType.train.SeatType;
import lombok.Data;
import java.util.*;
import java.util.stream.Collectors;
@Data
public class Train {
    private final Long id;
    private final String trainNum;
    private final List<Carriage> carriages;
    private final List<String> stations;

    // 性能优化：按座位类型预索引车厢列表
    private final Map<SeatType, List<Carriage>> carriageCacheByType;

    private final Map<String,Integer> stationIndexMap;
    private final Map<String,Integer> carriageIndexMap;

    public Train(Long trainId,String trainNum, List<Carriage> carriages,List<String> stations) {
        this.id=trainId;
        this.trainNum = trainNum;
        this.carriages = carriages;
        this.stations=stations;
        this.carriageCacheByType = carriages.stream()
                .collect(Collectors.groupingBy(Carriage::getSeatType));

        int i=0;
        this.carriageIndexMap=new HashMap<>();
        for(Carriage c:carriages){
            carriageIndexMap.put(c.getCarNumber(),i);
            i++;
        }
        i=0;
        this.stationIndexMap=new HashMap<>();
        for(String s:stations){
            stationIndexMap.put(s,i);
            i++;
        }

    }

    /**
     * 根据座位类型获取所有匹配的车厢
     */
    public List<Carriage> getCarriagesByType(SeatType type) {
        return carriageCacheByType.getOrDefault(type, Collections.emptyList());
    }

    public SeatType getTypeByCarriageNum(String carNumber){
        return carriages.get(carriageIndexMap.get(carNumber)).getSeatType();
    }

    /**
     * 根据索引获取具体车厢对象
     */
    public Carriage getCarriage(int carIndex) {
        if (carIndex < 0 || carIndex >= carriages.size()) {
            throw new IllegalArgumentException("无效的车厢索引: " + carIndex);
        }
        return carriages.get(carIndex);
    }
    public int stationIndex(String stationName){
        return stationIndexMap.get(stationName);
    }
}