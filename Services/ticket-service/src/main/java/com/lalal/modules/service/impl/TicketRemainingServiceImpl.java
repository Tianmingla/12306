package com.lalal.modules.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.lalal.framework.cache.SafeCacheTemplate;
import com.lalal.modules.constant.cache.CacheConstant;
import com.lalal.modules.dto.TicketRemainingRequestDTO;
import com.lalal.modules.dto.TicketRemainingResultDTO;
import com.lalal.modules.entity.SeatDO;
import com.lalal.modules.entity.StationDO;
import com.lalal.modules.entity.TrainDO;
import com.lalal.modules.entity.TrainStationDO;
import com.lalal.modules.mapper.SeatMapper;
import com.lalal.modules.mapper.StationMapper;
import com.lalal.modules.mapper.TrainStationMapper;
import com.lalal.modules.service.TicketRemainingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 余票计算服务实现（对齐 fillTrainSearchResult 逻辑）
 *
 * 参考：TrainRoutePairServiceImpl.fillTrainSearchResult lines 210-277
 *
 * 缓存设计：
 *   Key:   TICKET::REMAINING::{trainId}::{date}::{seatType}
 *   Value: List<Integer> [r1, r2, ..., rn]
 *            n = 经停站数 - 1（区间数）
 *            ri = 第 i 个区间的余票数
 *
 * 批量计算（batchCalculateRemaining）：
 *   输入：
 *     trainIdList = [1, 2]
 *     seatTypes    = [1, 2]
 *     stationsMap  = {1=[北京,天津,济南] → 2区间, 2=[北京,上海] → 1区间}
 *
 *   步骤：
 *     1. 构建缓存键列表 remainingKeys
 *     2. 构建参数列表 remainingArgs
 *     3. safeBatchLGet 批量查询
 *     4. 缓存命中 → 直接返回 List<Integer>
 *     5. 缓存未命中 → Lambda 内批量查 t_seat 获取座位总数
 *        对每个区间填充相同的座位总数（t_ticket 废弃）
 *     6. 返回 Map<"trainId_seatType", List<Integer>>
 *
 *   输出示例：
 *     { "1_1"→[50,45], "1_2"→[30,30], "2_1"→[60], "2_2"→[40] }
 *
 * 单区间查询（getRemainingBySegment）：
 *   算法（参考 lines 330-340）：
 *     List<Integer> list = remainingMap.get(trainId + "_" + seatType);
 *     int min = list.subList(departureIndex, arrivalIndex).stream()
 *                   .min(Integer::compareTo).orElse(0);
 *
 *   例如：trainId=1, seatType=1, stations=[北京,天津,济南,上海]
 *     remainingList = [50, 45, 40]  (3个区间)
 *     查询 北京→济南：departureIndex=0, arrivalIndex=2
 *     → subList(0,2)=[50,45] → min=45
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketRemainingServiceImpl implements TicketRemainingService {

    private final SeatMapper seatMapper;
    private final SafeCacheTemplate safeCacheTemplate;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int CACHE_EXPIRE_DAYS = 3;
    private final TrainStationMapper trainStationMapper;

    // ==================== 批量计算（核心） ====================

    /**
     * 批量计算余票（对齐 fillTrainSearchResult）
     *
     * 步骤详解：
     *  Step 1: 构建缓存键
     *    remainingKeys = [
     *      "TICKET::REMAINING::1::2024-01-01::1",
     *      "TICKET::REMAINING::1::2024-01-01::2",
     *      "TICKET::REMAINING::2::2024-01-01::1",
     *      "TICKET::REMAINING::2::2024-01-01::2"
     *    ]
     *
     *  Step 2: 构建参数
     *    remainingArgs = [
     *      [1, 1], [1, 2], [2, 1], [2, 2]
     *    ]
     *
     *  Step 3: safeBatchLGet
     *    缓存命中 → 返回 List<List<Integer>>
     *    缓存未命中 → Lambda 执行：
     *      a) 提取 trainIds=[1,2], seatTypes=[1,2]
     *      b) 查询 t_seat: SELECT train_id, seat_type, COUNT(*) as cnt
     *                     WHERE train_id IN (1,2) AND seat_type IN (1,2)
     *                     GROUP BY train_id, seat_type
     *      c) 填充结果：
     *           trainId=1, seatType=1, seatCount=50, segmentCount=2
     *           → result[idx("1_1")] = [50, 50]
     *
     *  Step 4: 重组结果
     *     resultMap = {
     *       "1_1": [50, 50],
     *       "1_2": [30, 30],
     *       "2_1": [60],
     *       "2_2": [40]
     *     }
     */
    @Override
    public Map<String, List<Integer>> batchCalculateRemaining(
            List<Long> trainIdList,
            String date,
            List<Integer> seatTypes,
            Map<Long, List<String>> stationsMap) {

        if (trainIdList.isEmpty() || seatTypes.isEmpty()) {
            return Collections.emptyMap();
        }

        // Step 1: 构建批量缓存键
        List<String> remainingKeys = trainIdList.stream()
                .flatMap(trainId -> seatTypes.stream()
                        .map(seatType -> CacheConstant.trainTicketRemainingKey(trainId, date, seatType)))
                .toList();

        // Step 2: 构建参数
        List<Object[]> remainingArgs = trainIdList.stream()
                .flatMap(trainId -> seatTypes.stream()
                        .map(seatType -> new Object[]{trainId, seatType}))
                .toList();

        // Step 3: 构建索引 trainId_seatType → result 索引
        Map<String, Integer> remainingIndex = new HashMap<>();
        int idx = 0;
        for (Long trainId : trainIdList) {
            for (Integer seatType : seatTypes) {
                remainingIndex.put(trainId + "_" + seatType, idx++);
            }
        }

        // Step 4: 批量查询缓存 + 回填（对齐 fillTrainSearchResult lines 238-277）
        List<List<Integer>> remainingList = safeCacheTemplate.safeBatchLGet(
                remainingKeys,
                (List<Object[]> args) -> {
                    // Lambda 回填逻辑：批量查询 t_seat 获取座位总数

                    // 1. 提取 trainIds 和 seatTypes
                    Set<Long> trainIds = args.stream()
                            .map(a -> (Long) a[0])
                            .collect(Collectors.toSet());
                    Set<Integer> seatTypeSet = args.stream()
                            .map(a -> (Integer) a[1])
                            .collect(Collectors.toSet());

                    // 2. 初始化结果容器
                    Map<String, Integer> indexMap = new HashMap<>();
                    List<List<Integer>> result = new ArrayList<>(args.size());
                    for (int i = 0; i < args.size(); i++) {
                        indexMap.put(args.get(i)[0] + "_" + args.get(i)[1], i);
                        result.add(new ArrayList<>());
                    }

                    // 3. 批量查询座位总数
                    // SELECT train_id, seat_type, COUNT(*) as cnt
                    // FROM t_seat
                    // WHERE train_id IN (...) AND seat_type IN (...)
                    // GROUP BY train_id, seat_type
                    QueryWrapper<SeatDO> wrapper = new QueryWrapper<SeatDO>()
                            .select("train_id", "seat_type", "COUNT(*) as cnt")
                            .in("train_id", trainIds)
                            .in("seat_type", seatTypeSet)
                            .groupBy("train_id", "seat_type");

                    List<Map<String, Object>> rows = seatMapper.selectMaps(wrapper);

                    // 4. 填充结果（关键逻辑）
                    for (Map<String, Object> row : rows) {
                        Long trainId = (Long) row.get("train_id");
                        Integer seatType = (Integer) row.get("seat_type");
                        Integer seatCount = ((Long) row.get("cnt")).intValue();

                        String key = trainId + "_" + seatType;
                        Integer resultIdx = indexMap.get(key);
                        if (resultIdx != null) {
                            // 获取该车次的区间数 = 站点数 - 1
                            List<String> stations = stationsMap.get(trainId);
                            int segmentCount = (stations != null && stations.size() > 1)
                                    ? stations.size() - 1
                                    : 1;

                            // 每个区间填充相同的座位总数
                            for (int i = 0; i < segmentCount; i++) {
                                result.get(resultIdx).add(seatCount);
                            }
                        }
                    }

                    return result;
                },
                new TypeReference<Integer>() {},
                remainingArgs,
                CACHE_EXPIRE_DAYS,
                TimeUnit.DAYS
        );

        // Step 5: 重组结果
        Map<String, List<Integer>> resultMap = new HashMap<>();
        for (Map.Entry<String, Integer> entry : remainingIndex.entrySet()) {
            String key = entry.getKey();
            Integer listIdx = entry.getValue();
            resultMap.put(key, remainingList.get(listIdx));
        }

        return resultMap;
    }

    // ==================== 单区间查询 ====================

    /**
     * 查询单个区间的余票（对齐 fillTrainSearchResult 区间计算）
     *
     * 算法（参考 lines 330-340）：
     *   Integer count = remainingTicketMap
     *       .get(trainId + "_" + seatType)
     *       .stream()
     *       .skip(departureIndex)
     *       .limit(arrivalIndex - departureIndex)
     *       .min(Integer::compareTo)
     *       .orElse(0);
     *
     * 例如：trainId=1, seatType=1, stations=[北京,天津,济南,上海]
     *   remainingList = [50, 45, 40]  (3个区间)
     *   查询 北京→济南：departureIndex=0, arrivalIndex=2
     *   → subList(0,2) = [50, 45] → min = 45
     */
    @Override
    public Integer getRemainingBySegment(Long trainId,
                                        String date,
                                        Integer seatType,
                                        String departure,
                                        String arrival) {
        // 参数校验
        if (departure==null || arrival==null) {
            return 0;
        }
        List<String> stations = safeCacheTemplate.safeGet(
                CacheConstant.trainStation(trainId),
                () -> trainStationMapper.selectList(new LambdaQueryWrapper<TrainStationDO>()
                        .select(TrainStationDO::getStationName)
                        .eq(TrainStationDO::getTrainId,trainId)
                        .orderByAsc(TrainStationDO::getSequence))
                        .stream()
                        .map(TrainStationDO::getStationName)
                        .toList(),
                new TypeReference<List<String>>() {},
                3,
                TimeUnit.DAYS
        );
        int departureIndex=-1;
        int arrivalIndex=-1;
        for(int i=0;i<stations.size();i++){
            if (Objects.equals(stations.get(i), arrival)){
                arrivalIndex=i;
            }
            if (Objects.equals(stations.get(i), departure)){
                departureIndex=i;
            }
        }
        // 查询座位总数（缓存）
        Integer seatCount = getSeatCount(trainId, seatType);
        if (seatCount == null || seatCount == 0) {
            return 0;
        }

        // 查询缓存中的余票列表
        String cacheKey = CacheConstant.trainTicketRemainingKey(trainId, date, seatType);

        List<Integer> remainingList = safeCacheTemplate.safeLGet(
                cacheKey,
                () -> {
                    // 缓存未命中：查询 t_seat 获取座位总数
                    Integer count = getSeatCountFromDB(trainId, seatType);
                    if (count == null) count = 0;

                    List<Integer> result=new ArrayList<>();
                    for(int i=0;i<stations.size()-1;i++){
                        result.add(count);
                    }
                    return result;
                },
                new TypeReference<Integer>() {},
                CACHE_EXPIRE_DAYS,
                TimeUnit.DAYS
        );

        if (remainingList == null || remainingList.isEmpty()) {
            return seatCount; // 默认全有票
        }

        // 计算区间 [departureIndex, arrivalIndex) 的最小余票
        int start = Math.min(departureIndex, remainingList.size() - 1);
        int end = Math.min(arrivalIndex, remainingList.size());

        if (start >= end) {
            return remainingList.get(remainingList.size() - 1);
        }

        return remainingList.subList(start, end).stream()
                .filter(Objects::nonNull)
                .min(Integer::compareTo)
                .orElse(0);
    }



    // ==================== 对外接口 ====================

    @Override
    public List<TicketRemainingResultDTO> batchGetRemainingTickets(List<TicketRemainingRequestDTO> requests) {
        // 按 trainId_seatType 分组
        Map<String, List<TicketRemainingRequestDTO>> grouped = requests.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getTrainId() + "_" + r.getSeatType()
                ));

        // 批量查询座位总数
        Map<String, Integer> seatCountCache = new HashMap<>();
        for (String key : grouped.keySet()) {
            Long trainId = Long.valueOf(key.split("_")[0]);
            Integer seatType = Integer.valueOf(key.split("_")[1]);
            Integer seatCount = getSeatCount(trainId, seatType);
            seatCountCache.put(key, seatCount);
        }

        // 构建结果
        List<TicketRemainingResultDTO> results = new ArrayList<>();
        for (TicketRemainingRequestDTO request : requests) {
            String key = request.getTrainId() + "_" + request.getSeatType();
            Integer seatCount = seatCountCache.get(key);

            results.add(TicketRemainingResultDTO.builder()
                    .trainId(request.getTrainId())
                    .trainNumber(request.getTrainNumber())
                    .departureStation(request.getDepartureStation())
                    .arrivalStation(request.getArrivalStation())
                    .seatType(request.getSeatType())
                    .remainingTickets(seatCount)
                    .hasAvailable(seatCount != null && seatCount > 0)
                    .build());
        }

        return results;
    }

    // ==================== 辅助方法 ====================
    /**
     * 从数据库查询座位总数
     */
    private Integer getSeatCountFromDB(Long trainId, Integer seatType) {
        Long count = seatMapper.selectCount(new LambdaQueryWrapper<SeatDO>()
                .eq(SeatDO::getTrainId, trainId)
                .eq(seatType != null, SeatDO::getSeatType, seatType));
        return count != null ? count.intValue() : 0;
    }

    /**
     * 获取座位总数（缓存）
     */
    public Integer getSeatCount(Long trainId, Integer seatType) {
        String cacheKey = CacheConstant.trainSeatCountKey(trainId, seatType);

        return safeCacheTemplate.safeGet(
                cacheKey,
                () -> getSeatCountFromDB(trainId, seatType),
                new TypeReference<Integer>() {},
                CACHE_EXPIRE_DAYS,
                TimeUnit.DAYS
        );
    }
}
