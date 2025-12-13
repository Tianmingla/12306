package com.lalal.modules.task;

import com.lalal.modules.entity.StationDO;
import com.lalal.modules.entity.TrainStationDO;
import com.lalal.modules.entity.TrainRoutePairDO;
import com.lalal.modules.mapper.StationMapper;
import com.lalal.modules.mapper.TrainStationMapper;
import com.lalal.modules.mapper.TrainRoutePairMapper;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Component
public class TrainRoutePairUpdateTask {

    @Autowired
    private TrainStationMapper trainStationMapper;
    @Autowired
    private TrainRoutePairMapper trainRoutePairMapper;
    @Autowired
    private StationMapper stationMapper;
    /**
     * 每月1日凌晨2点自动更新线路扁平化表
     */
    @Scheduled(cron = "0 0 2 1 * ?")
    @Transactional

    public void updateTrainRoutePairTable() {
        // 查询所有车次的站台关系
        List<TrainStationDO> stationList = trainStationMapper.selectList(null);
        //查询所有站台
        List<StationDO> stationDOS=stationMapper.selectList(null);
        //建立站台区域映射 或者说是给这个数组建立站台名索引
        Map<String,Integer> stationNameIndex=new HashMap<>();
        for(int i=0;i<stationDOS.size();i++){
            stationNameIndex.put(stationDOS.get(i).getName(),i);
        }
        // 按车次分组
        Map<String, List<TrainStationDO>> trainMap = new HashMap<>();
        for (TrainStationDO station : stationList) {
            trainMap.computeIfAbsent(station.getTrainNumber(), k -> new ArrayList<>()).add(station);
        }
        List<TrainRoutePairDO> routePairs = new ArrayList<>();
        // 遍历每个车次，生成所有区间线路
        for (Map.Entry<String, List<TrainStationDO>> entry : trainMap.entrySet()) {
            String trainNumber=entry.getKey();
            List<TrainStationDO> stations = entry.getValue();
            stations.sort(Comparator.comparing(TrainStationDO::getSequence));
            for (int i = 0; i < stations.size() - 1; i++) {
                for (int j = i + 1; j < stations.size(); j++) {
                    TrainRoutePairDO pair = new TrainRoutePairDO();
                    String departureStation=stations.get(i).getStationName();
                    String arrivalStation=stations.get(j).getStationName();
                    if(!stationNameIndex.containsKey(departureStation)||
                            !stationNameIndex.containsKey(arrivalStation)
                    ) continue;
                    pair.setTrainId(stations.get(i).getTrainId());
                    pair.setTrainNumber(stations.get(i).getTrainNumber());
                    pair.setDepartureStation(stations.get(i).getStationName());
                    pair.setArrivalStation(stations.get(j).getStationName());
                    pair.setStartTime(stations.get(i).getDepartureTime());
                    pair.setEndTime(stations.get(j).getArrivalTime());
                    pair.setStartRegion(stationDOS
                            .get(stationNameIndex
                                    .get(departureStation))
                            .getRegionName());
                    pair.setEndRegion(stationDOS
                            .get(stationNameIndex
                                    .get(arrivalStation))
                            .getRegionName());
                    routePairs.add(pair);
                }
            }
        }
        // 批量插入或更新
        // TODO: 可优化为批量 upsert
        for (TrainRoutePairDO pair : routePairs) {
            try {
                trainRoutePairMapper.insert(pair);
            }catch (Exception e){

            }
        }
    }

    /**
     * 手动触发一次更新
     */
    public void manualUpdate() {
        updateTrainRoutePairTable();
    }

    // TODO: 消息队列事件触发更新，待选型
}
