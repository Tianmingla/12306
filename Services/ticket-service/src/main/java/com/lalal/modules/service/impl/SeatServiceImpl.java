package com.lalal.modules.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.lalal.framework.cache.SafeCacheTemplate;
import com.lalal.modules.constant.cache.CacheConstant;
import com.lalal.modules.entity.SeatDO;
import com.lalal.modules.mapper.SeatMapper;
import com.lalal.modules.service.SeatService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@AllArgsConstructor
public class SeatServiceImpl extends ServiceImpl<SeatMapper, SeatDO> implements SeatService {
    private final SafeCacheTemplate safeCacheTemplate;
    private final SeatMapper seatMapper;
    /**
     * 批量获取座位类型列表
     */
    @Override
    public Map<Long, List<Integer>> batchGetSeatTypes(List<Long> tIds) {
        List<String> seatTypeKeys=tIds.stream()
                .map(CacheConstant::trainSeatType)
                .toList();
        List<Object[]> seatTypeArgs=tIds.stream()
                .map(t-> new Object[]{t})
                .toList();

        List<List<Integer>> seatTypeList=safeCacheTemplate.safeBatchGet(
                seatTypeKeys,
                (List<Object[]> args)->{
                    List<Long> trainIds=args.stream()
                            .map(arg->(Long)arg[0])
                            .toList();
                    Map<Long,Integer> indexmap=new HashMap<>();
                    List<List<Integer>> result=new ArrayList<>(args.size());
                    for(int i=0;i<trainIds.size();i++){
                        indexmap.put(trainIds.get(i),i);
                        result.add(new ArrayList<>());
                    }
                    LambdaQueryWrapper<SeatDO> lambdaQueryWrapper=new LambdaQueryWrapper<SeatDO>()
                            .select(SeatDO::getSeatType,SeatDO::getTrainId)
                            .in(SeatDO::getTrainId,trainIds)
                            .groupBy(SeatDO::getSeatType,SeatDO::getTrainId);
                    List<Map<String,Object>> objects=seatMapper.selectMaps(lambdaQueryWrapper);
                    for (Map<String,Object> objectMap:objects){
                        result.get(indexmap.get(objectMap.get("train_id"))).add((Integer) objectMap.get("seat_type"));
                    }
                    return result;
//
                },
                new TypeReference<List<Integer>>(){},
                seatTypeArgs,
                3,
                TimeUnit.DAYS
        );
        Map<Long,List<Integer>> seatTypemap=new HashMap<>();
        for (int i=0;i<tIds.size();i++){
            seatTypemap.put(tIds.get(i),seatTypeList.get(i));
        }

        return seatTypemap;
    }
}
