package com.lalal.modules.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.lalal.framework.cache.SafeCacheTemplate;
import com.lalal.modules.constant.cache.CacheConstant;
import com.lalal.modules.dto.TicketRemainingRequestDTO;
import com.lalal.modules.dto.TicketRemainingResultDTO;
import com.lalal.modules.entity.SeatDO;
import com.lalal.modules.entity.TrainStationDO;
import com.lalal.modules.mapper.SeatMapper;
import com.lalal.modules.mapper.TrainStationMapper;
import com.lalal.modules.service.SeatService;
import com.lalal.modules.service.TicketRemainingService;
import com.lalal.modules.service.TrainStationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
    private final SeatService seatService;
    private final TrainStationService trainStationService;
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
            Map<Long, List<Integer>> seatTypemap,
            Map<Long, List<String>> stationsmap) {

        if (trainIdList.isEmpty()) {
            return Collections.emptyMap();
        }
        if(seatTypemap==null){
            seatTypemap=seatService.batchGetSeatTypes(trainIdList);
        }
        if(stationsmap==null){
            stationsmap=trainStationService.batchGetStationNames(trainIdList);
        }

        Map<Long, List<Integer>> finalSeatTypemap = seatTypemap;
        List<String> remainingTicketKeys=trainIdList.stream()
                .flatMap((t)->
                        finalSeatTypemap.get(t).stream()
                                .map(s->CacheConstant.trainTicketRemainingKey(
                                        t,
                                        date,
                                        s
                                ))
                )
                .toList();
        List<Object[]> remainingTicketArgs=trainIdList.stream()
                .flatMap((t)->
                        finalSeatTypemap.get(t)
                                .stream()
                                .map(s->new Object[]{t,s})
                )
                .toList();
        Map<String,Integer> remainingTicketIndex=new HashMap<>();
        int recordIdx=0;
        for(int i=0;i<trainIdList.size();i++){
            List<Integer> seatTypes=seatTypemap.get(trainIdList.get(i));
            for(int j=0;j<seatTypes.size();j++){
                remainingTicketIndex.put(
                        trainIdList.get(i)+"_"+seatTypes.get(j),
                        recordIdx++
                );
            }
        }
        Map<Long, List<String>> finalStationsmap = stationsmap;
        List<List<Integer>> remainingTicketList=safeCacheTemplate.safeBatchLGet(
                remainingTicketKeys,
                (List<Object[]> args)->{
                    List<Long> trainIds=args.stream()
                            .map(arg->(Long)arg[0])
                            .toList();
                    List<Integer> seatTypes=args.stream()
                            .map(arg->(Integer)arg[1])
                            .toList();
                    Map<String,Integer> indexmap=new HashMap<>();
                    List<List<Integer>> result=new ArrayList<>(args.size());
                    for(int i=0;i<trainIds.size();i++){
                        indexmap.put(trainIds.get(i)+"_"+seatTypes.get(i),i);
                        result.add(new ArrayList<>());
                    }
                    //TODO 并不是任意匹配 但数据库不可能出现该火车id和其他座位类型的数据 因此只是性能浪费
                    //等到不懒的时候推荐改成xml做（train_id,seat_type）in (...)
                    QueryWrapper wrapper=new QueryWrapper<SeatDO>()
                            .select("train_id","seat_type","count(*) as count")
                            .in("train_id",trainIds)
                            .in("seat_type",seatTypes)
                            .groupBy("train_id","seat_type");
                    List<Map<String,Object>> objs=seatMapper.selectMaps(wrapper);
                    //TODO 高可用 暂时以缓存为中心 若没有说明全有票
                    for(Map<String,Object> obj :objs){
                        Long trainId=(Long) obj.get("train_id");
                        Integer seatType=(Integer) obj.get("seat_type");
                        Long count = (Long) obj.get("count");
                        String indexKey=trainId + "_" + seatType;
                        for(int i = 0; i< finalStationsmap.get(trainId).size()-1; i++) {
                            result.get(indexmap.get(indexKey)).add(count.intValue());
                        }
                    }
                    return result;
                },
                new TypeReference<Integer>(){},
                remainingTicketArgs,
                3,
                TimeUnit.DAYS
        );
        Map<String,List<Integer>> remainingTicketmap=new HashMap<>();
        for(Map.Entry<String,Integer> idx:remainingTicketIndex.entrySet()){
            remainingTicketmap.put(idx.getKey(),remainingTicketList.get(idx.getValue()));
        }
        return remainingTicketmap;
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
