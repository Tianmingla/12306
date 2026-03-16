package com.lalal.modules.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.lalal.framework.cache.SafeCacheTemplate;
import com.lalal.modules.constant.cache.CacheConstant;
import com.lalal.modules.dao.CarriageDO;
import com.lalal.modules.dao.SeatDO;
import com.lalal.modules.dao.TrainDO;
import com.lalal.modules.dto.request.SeatSelectionRequestDTO;
import com.lalal.modules.dto.response.TicketDTO;


import com.lalal.modules.enumType.train.SeatType;
import com.lalal.modules.mapper.CarriageMapper;
import com.lalal.modules.mapper.SeatMapper;
import com.lalal.modules.mapper.TrainMapper;
import com.lalal.modules.model.Carriage;
import com.lalal.modules.strategy.SeatSelectionStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SeatSelectionServiceImpl implements SeatSelectionService {
    private final TrainMapper trainMapper;
    private final CarriageMapper carriageMapper;
    private final SeatMapper seatMapper;
    private final List<SeatSelectionStrategy> strategies;
    private final Map<Integer, SeatSelectionStrategy> strategyMap = new ConcurrentHashMap<>();
    private final SafeCacheTemplate safeCacheTemplate;

    @PostConstruct
    public void init() {
        for (SeatSelectionStrategy s : strategies) {
            strategyMap.put(s.getSeatType(), s);
        }
    }

    @Override
    public TicketDTO select(SeatSelectionRequestDTO request) {
        if (request.getPassengers() == null || request.getPassengers().isEmpty()) {
            return null;
        }

        // 1. 获取列车信息
        TrainDO train=safeCacheTemplate.safeGet(
                CacheConstant.trainCodeToDetail(request.getTrainNum()),
                () -> trainMapper.selectOne(new LambdaQueryWrapper<TrainDO>()
                        .eq(TrainDO::getTrainNumber, request.getTrainNum())),
                new TypeReference<TrainDO>() {},
                3,
                TimeUnit.DAYS
        );
        if (train == null) return null;

        // 2. 确定座位类型
        // 这里简化处理：假设一次请求中的所有乘客选择相同的座位类型
        //TODO 分组
        int seatType;
        try {
            seatType = SeatType.findByDesc(request.getPassengers().get(0).getSeatType()).getCode();
        } catch (NumberFormatException e) {
            // 如果是字符串描述，可能需要转换，这里假设是数字字符串
            return null;
        }

        // 3. 获取对应车厢
        List<CarriageDO> carriages=safeCacheTemplate.safeGet(
                CacheConstant.trainCarriage(train.getId()),
                ()-> carriageMapper.selectList(new LambdaQueryWrapper<CarriageDO>()
                        .eq(CarriageDO::getTrainId, train.getId())
                        .eq(CarriageDO::getCarriageType, seatType)),
                new TypeReference<List<CarriageDO>>(){},
                3,
                TimeUnit.DAYS
        );
        List<Object[]> Args=carriages.stream()
                .map((c)-> new Object[]{c.getCarriageNumber()})
                .collect(Collectors.toCollection(ArrayList::new));
        List<String> carriageCountKeys=carriages.stream()
                .map((c)->CacheConstant.trainCarriageCount(train.getId(), c.getCarriageNumber()))
                .toList();
        List<Integer> counts=safeCacheTemplate.safeBatchGet(
                carriageCountKeys,
                (args)->{
                    //TODO  检查mysql索引
                    List<String> carriageNumbers=args.stream()
                            .map((a)->(String)a[0])
                            .toList();
                    Map<String,Integer> integerMap=new HashMap<>();
                    List<Integer> result=new ArrayList<>(carriageNumbers.size());
                    for(int i=0;i<carriageNumbers.size();i++){
                        integerMap.put(carriageNumbers.get(i),i);
                        result.add(null);
                    }
                    QueryWrapper<SeatDO> carriageDOWrapper=new QueryWrapper<>();
                    carriageDOWrapper.select("carriage_number","count(*) as count")
                            .in("carriage_number",carriageNumbers)
                            .eq("train_id",train.getId())
                            .groupBy("carriage_number");
                    List<Map<String,Object>> countMap=seatMapper.selectMaps(carriageDOWrapper);

                    for(Map<String,Object> map :countMap){
                        result.set(integerMap.get(map.get("carriage_number")),((Long)map.get("count")).intValue());
                    }
                    return result;

                },
                new TypeReference<Integer>(){},
                Args,
                3,
                TimeUnit.DAYS
        );
        for(int i=0;i<carriages.size();i++){
            carriages.get(i).setSeatCount(counts.get(i));
        }

        // 4. 执行选座策略
        SeatSelectionStrategy strategy = strategyMap.get(seatType);
        if (strategy == null) return null;

        return strategy.select(request, carriages);
    }
}
