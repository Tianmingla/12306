package com.lalal.modules.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.lalal.framework.cache.SafeCacheTemplate;
import com.lalal.modules.constant.cache.CacheConstant;
import com.lalal.modules.entity.TrainDO;
import com.lalal.modules.entity.TrainRoutePairDO;
import com.lalal.modules.entity.TrainStationDO;
import com.lalal.modules.mapper.TrainMapper;
import com.lalal.modules.mapper.TrainStationMapper;
import com.lalal.modules.service.TrainStationService;
import lombok.AllArgsConstructor;
import com.lalal.modules.dto.response.TrainStationDetailRespDTO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class TrainStationServiceImpl extends ServiceImpl<TrainStationMapper, TrainStationDO> implements TrainStationService {
    SafeCacheTemplate safeCacheTemplate;
    TrainStationMapper trainStationMapper;
    TrainMapper trainMapper;

    @Override
    public List<TrainStationDetailRespDTO> getStationDetailsByTrainNum(String trainNum) {
        String detailCacheKey = CacheConstant.trainCodeToDetail(trainNum);
        TrainDO trainDO = safeCacheTemplate.safeGet(
                detailCacheKey,
                () -> {
                    LambdaQueryWrapper<TrainDO> lambdaQueryWrapper = new LambdaQueryWrapper<TrainDO>()
                            .eq(TrainDO::getTrainNumber, trainNum);
                    return trainMapper.selectOne(lambdaQueryWrapper);
                },
                new TypeReference<TrainDO>() {},
                10,
                TimeUnit.DAYS
        );

        if (trainDO == null) {
            return new ArrayList<>();
        }


        return safeCacheTemplate.safeGet(
                    CacheConstant.trainStationDetail(trainDO.getId()),
                    ()->{
                        LambdaQueryWrapper<TrainStationDO> lambdaQueryWrapper = new LambdaQueryWrapper<TrainStationDO>()
                                .eq(TrainStationDO::getTrainId, trainDO.getId())
                                .orderByAsc(TrainStationDO::getSequence);
                        List<TrainStationDO> stations = trainStationMapper.selectList(lambdaQueryWrapper);
                        return stations;
                    },
                    new TypeReference<List<TrainStationDO>>(){},
                    10,
                    TimeUnit.DAYS
            ).stream().map(station -> {
                TrainStationDetailRespDTO dto = new TrainStationDetailRespDTO();
                dto.setStationName(station.getStationName());
                dto.setArrivalTime(station.getArrivalTime());
                dto.setDepartureTime(station.getDepartureTime());
                dto.setStopoverTime(station.getStopoverTime());
                return dto;
            }).collect(Collectors.toList());
    }

    @Override
    public List<String> getStationNamesByTrainNum(String trainNum) {
        String detailCacheKey=CacheConstant.trainCodeToDetail(trainNum);
        TrainDO trainDO=safeCacheTemplate.safeGet(
                detailCacheKey,
                ()->{
                    LambdaQueryWrapper<TrainDO> lambdaQueryWrapper=new LambdaQueryWrapper<TrainDO>()
                            .eq(TrainDO::getTrainNumber,trainNum);
                    return trainMapper.selectOne(lambdaQueryWrapper);
                },
                new TypeReference<TrainDO>(){},
                10,
                TimeUnit.DAYS
        );
        String CacheKey=CacheConstant.trainStation(trainDO.getId());
        return safeCacheTemplate.safeGet(
                CacheKey,
                ()->{
                    //TODO 检查索引的建立
                    LambdaQueryWrapper<TrainStationDO> lambdaQueryWrapper=new LambdaQueryWrapper<TrainStationDO>()
                            .select(TrainStationDO::getStationName)
                            .eq(TrainStationDO::getTrainId,trainDO.getId())
                            .orderByAsc(TrainStationDO::getSequence);
                    List<Object> objs=trainStationMapper.selectObjs(lambdaQueryWrapper);
                    return objs
                            .stream()
                            .map(o->(String)o)
                            //一定要用ArrayList toLIst不可变集合如果是空集序列化到redis类型擦除 反序列化报错
                            .collect(Collectors.toCollection(ArrayList::new));
                },
                new TypeReference<List<String>>(){},
                10,
                TimeUnit.DAYS
        );
    }

    @Override
    public List<List<String>> getStationNamesByTrainIds(List<Long> trainIds) {
        List<String> stationsKeys=trainIds.stream()
                .map(CacheConstant::trainStation)
                .toList();
        //性能考虑 直接用s
        List<Object[]> stationsArgs=trainIds.stream()
                .map(t-> new Object[]{t})
                .toList();
        return safeCacheTemplate.safeBatchGet(
                stationsKeys,
                (List<Object[]> args)->{
                    List<Long> _trainIds=args.stream()
                            .map(arg->(Long)arg[0])
                            .toList();
                    Map<Long,Integer> indexmap=new HashMap<>();
                    List<List<String>> result=new ArrayList<>(args.size());
                    for(int i=0;i<_trainIds.size();i++){
                        indexmap.put(_trainIds.get(i),i);
                        result.add(new ArrayList<>());
                    }
                    LambdaQueryWrapper<TrainStationDO> lambdaQueryWrapper=new LambdaQueryWrapper<TrainStationDO>()
                            .select(TrainStationDO::getStationName,TrainStationDO::getTrainId)
                            .in(TrainStationDO::getTrainId,_trainIds)
                            .orderByAsc(TrainStationDO::getTrainId,TrainStationDO::getSequence);
                    List<Map<String,Object>> objects=trainStationMapper.selectMaps(lambdaQueryWrapper);
                    for (Map<String,Object> objectMap:objects){
                        result.get(indexmap.get(objectMap.get("train_id"))).add((String)objectMap.get("station_name"));
                    }
                    return result;
//
                },
                new TypeReference<List<String>>(){},
                stationsArgs,
                3,
                TimeUnit.DAYS
        );
    }
}
