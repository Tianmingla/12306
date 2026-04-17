package com.lalal.modules.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.lalal.framework.cache.SafeCacheTemplate;
import com.lalal.modules.constant.cache.CacheConstant;
import com.lalal.modules.dto.FareCalculationRequestDTO;
import com.lalal.modules.dto.FareCalculationResultDTO;
import com.lalal.modules.entity.*;
import com.lalal.modules.enumType.fare.PassengerTypeEnum;
import com.lalal.modules.graph.TrainEdge;
import com.lalal.modules.graph.TransitGraph;
import com.lalal.modules.mapper.*;
import com.lalal.modules.service.FareCalculationService;
import com.lalal.modules.service.TicketRemainingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 局部图构建器
 *
 * 思路：
 * 不构建全量图（全国几万个车站 × 每天几百趟车），而是根据查询条件动态构建局部图
 *
 * 步骤：
 * 1. 找出所有从出发站出发的列车
 * 2. 找出所有到达目的站的列车
 * 3. 找出连接这些列车的中转站
 * 4. 只构建相关站点的子图
 *
 * 优点：搜索空间可控，结果准确
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class LocalGraphBuilder {

    private final TrainStationMapper trainStationMapper;
    private final TrainMapper trainMapper;
    private final TrainFareConfigMapper trainFareConfigMapper;
    private final SeatMapper seatMapper;
    private final TrainRoutePairMapper trainRoutePairMapper;
    private final TicketRemainingService ticketRemainingService;
    private final FareCalculationService fareCalculationService;

    // 默认参数
    private static final int DEFAULT_MAX_TRANSFER = 3;
    private static final int DEFAULT_MAX_DURATION = 480; // 8小时
    private final SafeCacheTemplate safeCacheTemplate;

    /**
     * 构建局部图
     *
     * @param from       出发站
     * @param to         目的站
     * @param date       乘车日期
     * @param maxTransfer 最大换乘次数
     * @param maxDuration 最大历时（分钟）
     * @return 局部交通图
     */
    public TransitGraph buildLocalGraph(String from, String to, LocalDate date,
                                        int maxTransfer, int maxDuration) {
        TransitGraph graph = new TransitGraph();

        // Step 1: 收集所有相关车次
        Set<String> relevantTrainNumbers = new HashSet<>();
        Map<String, TrainDO> trainMap = new HashMap<>();

//        // 1.1 从出发站的列车
//        List<TrainStationDO> depTrains = trainStationMapper.selectList(
//                new LambdaQueryWrapper<TrainStationDO>()
//                        .eq(TrainStationDO::getStationName, from)
//                        .eq(TrainStationDO::getRunDate, date)
//        );
//
//        for (TrainStationDO ts : depTrains) {
//            relevantTrainNumbers.add(ts.getTrainNumber());
//        }
//
//        // 1.2 到达目的站的列车
//        List<TrainStationDO> arrTrains = trainStationMapper.selectList(
//                new LambdaQueryWrapper<TrainStationDO>()
//                        .eq(TrainStationDO::getStationName, to)
//                        .eq(TrainStationDO::getRunDate, date)
//        );
//
//        for (TrainStationDO ts : arrTrains) {
//            relevantTrainNumbers.add(ts.getTrainNumber());
//        }

        // 1.3 路过出发站和目的站的所有列车（可能有更优方案）
        // 找出所有经过 from 或 to 的列车，减少到一定程度的车次
        List<TrainStationDO> viaTrains = trainStationMapper.selectList(
                new LambdaQueryWrapper<TrainStationDO>()
                        .in(TrainStationDO::getStationName, Arrays.asList(from, to))
                        .eq(TrainStationDO::getRunDate, date)
        );

        for (TrainStationDO ts : viaTrains) {
            relevantTrainNumbers.add(ts.getTrainNumber());
        }

        if (relevantTrainNumbers.isEmpty()) {
            log.warn("未找到相关车次: from={}, to={}, date={}", from, to, date);
            return graph;
        }

        log.info("收集到 {} 个相关车次", relevantTrainNumbers.size());

        // Step 2: 加载列车详情
        List<TrainDO> trains = trainMapper.selectList(
                new LambdaQueryWrapper<TrainDO>()
                        .in(TrainDO::getTrainNumber, relevantTrainNumbers)
        );

        for (TrainDO train : trains) {
            trainMap.put(train.getTrainNumber(), train);
        }

        // Step 3: 加载列车经停站信息（构建完整的节点）
        List<TrainStationDO> allStations = trainStationMapper.selectList(
                new LambdaQueryWrapper<TrainStationDO>()
                        .in(TrainStationDO::getTrainNumber, relevantTrainNumbers)
                        .eq(TrainStationDO::getRunDate, date)
                        .orderByAsc(TrainStationDO::getSequence)
        );

        // 按车次分组
        Map<String, List<TrainStationDO>> stationsByTrain = allStations.stream()
                .collect(Collectors.groupingBy(TrainStationDO::getTrainNumber));

        // Step 4: 为每个车次的每段区间创建边
        for (Map.Entry<String, List<TrainStationDO>> entry : stationsByTrain.entrySet()) {
            String trainNumber = entry.getKey();
            List<TrainStationDO> stations = entry.getValue();
            TrainDO train = trainMap.get(trainNumber);

            if (train == null || stations.size() < 2) continue;

            // 获取座位类型和余票信息
            List<Integer> seatTypes = getSeatTypes(train.getId());
            List<TrainEdge.SeatPrice> seatPrices = getSeatPrices(train, stations,seatTypes);
            List<TrainEdge.SeatRemaining> seatRemainings = getSeatRemainings(train, stations);


            for (int i = 0; i < stations.size() - 1; i++) {
                TrainStationDO fromStation = stations.get(i);
                TrainStationDO toStation = stations.get(i + 1);

                // 计算出发和到达时间（含日期偏移）
                LocalDateTime departureDateTime = buildDateTime(date, fromStation.getDepartureTime(), 0);
                LocalDateTime arrivalDateTime = buildDateTime(date, toStation.getArrivalTime(),toStation.getArriveDayDiff());

                // 跳过跨天超过限制的情况
                if (java.time.Duration.between(departureDateTime, arrivalDateTime).toMinutes() > maxDuration) {
                    continue;
                }

                // 添加乘车边
                graph.addTrainEdge(
                        trainNumber,
                        train.getTrainType(),
                        fromStation.getStationName(),
                        departureDateTime,
                        toStation.getStationName(),
                        arrivalDateTime,
                        seatTypes,
                        seatPrices,
                        seatRemainings
                );
            }
        }

        // Step 5: 添加换乘等待边
        for (String station : graph.getAllStations()) {
            graph.addTransferWaitEdges(station);
        }

        log.info("构建局部图完成: nodes={}, edges={}", graph.nodeCount(), graph.edgeCount());
        return graph;
    }

    /**
     * 构建局部图（使用默认参数）
     */
    public TransitGraph buildLocalGraph(String from, String to, LocalDate date) {
        return buildLocalGraph(from, to, date, DEFAULT_MAX_TRANSFER, DEFAULT_MAX_DURATION);
    }

    /**
     * 构建完整图（全量数据）
     * 适用于离线预计算场景
     */
    public TransitGraph buildFullGraph(LocalDate date) {
        TransitGraph graph = new TransitGraph();

        List<TrainStationDO> allStations = trainStationMapper.selectList(
                new LambdaQueryWrapper<TrainStationDO>()
                        .eq(TrainStationDO::getRunDate, date)
                        .orderByAsc(TrainStationDO::getSequence)
        );

        Map<String, List<TrainStationDO>> stationsByTrain = allStations.stream()
                .collect(Collectors.groupingBy(TrainStationDO::getTrainNumber));

        for (Map.Entry<String, List<TrainStationDO>> entry : stationsByTrain.entrySet()) {
            String trainNumber = entry.getKey();
            List<TrainStationDO> stations = entry.getValue();

            TrainDO train = trainMapper.selectOne(
                    new LambdaQueryWrapper<TrainDO>()
                            .eq(TrainDO::getTrainNumber, trainNumber)
            );

            if (train == null || stations.size() < 2) continue;

            List<Integer> seatTypes = getSeatTypes(train.getId());
            List<TrainEdge.SeatPrice> seatPrices = getSeatPrices(train, stations,seatTypes);
            List<TrainEdge.SeatRemaining> seatRemainings = getSeatRemainings(train, stations);

            for (int i = 0; i < stations.size() - 1; i++) {
                TrainStationDO fromStation = stations.get(i);
                TrainStationDO toStation = stations.get(i + 1);

                LocalDateTime departureDateTime = buildDateTime(date, fromStation.getDepartureTime(),
                        fromStation.getArriveDayDiff());
                LocalDateTime arrivalDateTime = buildDateTime(date, toStation.getArrivalTime(),
                        toStation.getArriveDayDiff());

                graph.addTrainEdge(
                        trainNumber,
                        train.getTrainType(),
                        fromStation.getStationName(),
                        departureDateTime,
                        toStation.getStationName(),
                        arrivalDateTime,
                        seatTypes,
                        seatPrices,
                        seatRemainings
                );
            }
        }

        for (String station : graph.getAllStations()) {
            graph.addTransferWaitEdges(station);
        }

        return graph;
    }

    // ============ 辅助方法 ============

    /**
     * 构建日期时间（含日期偏移）
     */
    private LocalDateTime buildDateTime(LocalDate baseDate, LocalTime time, int dayOffset) {
        return baseDate.plusDays(dayOffset).atTime(time);
    }

    /**
     * 获取座位类型列表
     */
    private List<Integer> getSeatTypes(Long trainId) {
        return safeCacheTemplate.safeGet(
                CacheConstant.trainSeatType(trainId),
                ()-> seatMapper.selectList(
                                new LambdaQueryWrapper<SeatDO>()
                                        .select(SeatDO::getSeatType)
                                        .eq(SeatDO::getTrainId, trainId)
                                        .groupBy(SeatDO::getSeatType)
                        ).stream()
                        .map(SeatDO::getSeatType)
                        .collect(Collectors.toList()),
                new TypeReference<List<Integer>>(){},
                3,
                TimeUnit.DAYS
        );
    }

    /**
     * 获取票价信息（按区间）
     */
    private List<TrainEdge.SeatPrice> getSeatPrices(TrainDO train, List<TrainStationDO> stations,List<Integer> seatTypes) {
        List<FareCalculationRequestDTO> fareCalculationRequestDTOS=new ArrayList<>();
        for (int i = 0; i < stations.size() - 1; i++) {
            String from = stations.get(i).getStationName();
            String to = stations.get(i + 1).getStationName();

            for(int seatType:seatTypes) {
                fareCalculationRequestDTOS.add(FareCalculationRequestDTO.builder()
                        .departureStation(from)
                        .arrivalStation(to)
                        .trainId(train.getId())
                        .trainNumber(train.getTrainNumber())
                        .seatType(seatType)
                        .isPeakSeason(false)
                        .trainBrand(train.getTrainBrand())
                        .passengerType(PassengerTypeEnum.ADULT.getCode())
                        .build()
                );
            }
        }
        return fareCalculationService.batchCalculateFare(fareCalculationRequestDTOS)
                .stream()
                .map(result-> new TrainEdge.SeatPrice(result.getSeatType(), result.getTotalFare()))
                .toList();
    }

    /**
     * 获取余票信息
     */
    private List<TrainEdge.SeatRemaining> getSeatRemainings(TrainDO train, List<TrainStationDO> stations) {
        return null;
    }
}
