package com.lalal.modules.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.lalal.framework.cache.SafeCacheTemplate;
import com.lalal.modules.constant.cache.CacheConstant;
import com.lalal.modules.dto.FareCalculationRequestDTO;
import com.lalal.modules.dto.FareCalculationResultDTO;
import com.lalal.modules.dto.response.TrainSearchResponseDTO;
import com.lalal.modules.entity.*;
import com.lalal.modules.dto.transfer.TransferRouteResult;
import com.lalal.modules.dto.transfer.TransferSegment;
import com.lalal.modules.enumType.train.SeatType;
import com.lalal.modules.mapper.*;
import com.lalal.modules.service.*;
import com.lalal.modules.template.CompositeKey2;
import com.lalal.modules.template.CompositeKey4;
import com.lalal.modules.utils.DateUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.cache.Cache;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class TrainRoutePairServiceImpl extends ServiceImpl<TrainRoutePairMapper, TrainRoutePairDO> implements TrainRoutePairService {


    private final TicketRemainingService ticketRemainingService;
    StationService stationService;
    SeatService seatService;
    SeatMapper seatMapper;
    TicketMapper ticketMapper;
    TrainRoutePairMapper trainRoutePairMapper;
    SafeCacheTemplate safeCacheTemplate;
    TrainStationService trainStationService;
    RedissonClient redissonClient;
    FareCalculationService fareCalculationService;
    TransferSearchService transferSearchService;
    @Override
    public List<TrainSearchResponseDTO> searchTrains(String from, String mid, String to, String date) {
        // 1. mid不为空，查找 from->mid->to
        if (mid != null && !mid.isEmpty()) {
            List<TrainSearchResponseDTO> result=handleMidMerge(from,mid,to);
            fillTrainSearchResult(result,date);
            return result;
        }
        // 2. mid为空，查找直达线路
        String directKey = CacheConstant.trainRouteKey(from, to);
        List<TrainRoutePairDO> direct = safeCacheTemplate.safeGet(
                directKey,
                () ->{
                    LambdaQueryWrapper<TrainRoutePairDO> wrapper=new LambdaQueryWrapper<TrainRoutePairDO>()
                            .eq(TrainRoutePairDO::getStartRegion,from)
                            .eq(TrainRoutePairDO::getEndRegion,to);
                    return trainRoutePairMapper.selectList(wrapper);
                },
                new TypeReference<List<TrainRoutePairDO>>(){},
                3,
                TimeUnit.DAYS
        );
        if (!direct.isEmpty()) {
            if(LocalDate.parse(date).equals(LocalDate.now())) {
                direct = direct.stream().filter(d -> {
                    //TODO 数据库数据不干净问题 数据清洗不当 先在这里过滤处理
                    if(d.getStartTime()==null){
                        return false;
                    }
                    return d.getStartTime().isAfter(LocalTime.now());
                }).toList();
            }
            List<TrainSearchResponseDTO> result=direct
                    .stream()
                    .map((e)->{
                        TrainSearchResponseDTO trainSearchResponseDTO=new TrainSearchResponseDTO();
                        trainSearchResponseDTO.setSegments(List.of(e));
                        trainSearchResponseDTO.setTransferCount(1);
                        return trainSearchResponseDTO;
                    })
                    .toList();
            fillTrainSearchResult(result,date);
            return result;
        }
        // 3. 无直达，调用智能中转服务
        log.info("无直达线路，启动智能中转搜索: from={}, to={}, date={}", from, to, date);
        List<TransferRouteResult> transferResults = transferSearchService.search(from, to, date);
        if (transferResults.isEmpty()) {
            log.info("智能中转也未找到线路: from={}, to={}", from, to);
            return List.of();
        }
        log.info("智能中转找到 {} 条线路", transferResults.size());
        List<TrainSearchResponseDTO> result=transferResults.stream()
                .map(this::convertTransferResultToResponseDTO)
                .toList();
        fillTrainSearchResult(result,date);
        return result;
    }
    private List<TrainSearchResponseDTO> handleMidMerge(String from,
                                                        String mid,
                                                        String to){
        String routeKey= CacheConstant.trainRouteKey(from,mid);
        String midRouteKey = CacheConstant.trainRouteKey(mid, to);

        // 查询 from->mid
        List<TrainRoutePairDO> firstLeg=safeCacheTemplate.safeGet(
                routeKey,
                ()->{
                    LambdaQueryWrapper<TrainRoutePairDO> wrapper=new LambdaQueryWrapper<TrainRoutePairDO>()
                            .eq(TrainRoutePairDO::getStartRegion,from)
                            .eq(TrainRoutePairDO::getEndRegion,mid);
                    return trainRoutePairMapper.selectList(wrapper);
                },
                new TypeReference<List<TrainRoutePairDO>>(){},
                3,
                TimeUnit.DAYS
        );
        // 查询 mid->to
        List<TrainRoutePairDO> secondLeg = safeCacheTemplate.safeGet(
                midRouteKey,
                ()->{
                    LambdaQueryWrapper<TrainRoutePairDO> wrapper=new LambdaQueryWrapper<TrainRoutePairDO>()
                            .eq(TrainRoutePairDO::getStartRegion,mid)
                            .eq(TrainRoutePairDO::getEndRegion,to);
                    return trainRoutePairMapper.selectList(wrapper);
                },
                new TypeReference<List<TrainRoutePairDO>>(){},
                3,
                TimeUnit.DAYS
        );
        //合并结果
        List<TrainSearchResponseDTO> result=new ArrayList<>();
        for(int i=0;i<firstLeg.size();i++){
            for(int j=0;j<secondLeg.size();j++){
                //保证结果是同站换乘
                if(Objects.equals(firstLeg.get(i).getArrivalStation(), secondLeg.get(j).getDepartureStation())) {
                    TrainSearchResponseDTO trainSearchResponseDTO = new TrainSearchResponseDTO();
                    trainSearchResponseDTO.setSegments(List.of(firstLeg.get(i), secondLeg.get(j)));
                    trainSearchResponseDTO.setTransferCount(2);
                    result.add(trainSearchResponseDTO);
                }
            }
        }
        return result;
    }

    /**
     * 将智能中转搜索结果(TransferRouteResult)转换为车次搜索响应(TrainSearchResponseDTO)
     * 使智能中转结果能够复用前端的渲染逻辑
     */
    private TrainSearchResponseDTO convertTransferResultToResponseDTO(TransferRouteResult transferResult) {
        TrainSearchResponseDTO response = new TrainSearchResponseDTO();
        response.setPlanId(transferResult.getRouteId());
        response.setTransferCount(transferResult.getTransferCount());
        response.setTotalDurationMinutes(transferResult.getTotalMinutes());
        response.setFirstDepartureTime(transferResult.getDepartureTime());
        response.setFinalArrivalTime(transferResult.getArrivalTime());

        List<TrainRoutePairDO> segments = transferResult.getSegments().stream()
                .map(seg -> {
                    TrainRoutePairDO pair = new TrainRoutePairDO();
                    pair.setTrainNumber(seg.getTrainNumber());
                    pair.setDepartureStation(seg.getDepartureStation());
                    pair.setArrivalStation(seg.getArrivalStation());
                    pair.setStartRegion(seg.getDepartureStation());
                    pair.setEndRegion(seg.getArrivalStation());
                    try {
                        pair.setStartTime(LocalTime.parse(seg.getDepartureTime()));
                    } catch (Exception e) {
                        pair.setStartTime(LocalTime.of(0, 0));
                    }
                    try {
                        pair.setEndTime(LocalTime.parse(seg.getArrivalTime()));
                    } catch (Exception e) {
                        pair.setEndTime(LocalTime.of(0, 0));
                    }
                    pair.setDayDiff(0);
                    return pair;
                })
                .toList();
        response.setSegments(segments);

        // 票价映射（TransferSegment.priceMap 已经由 FareCalculationService 计算）
        List<Map<String, BigDecimal>> priceMaps = transferResult.getSegments().stream()
                .map(TransferSegment::getPriceMap)
                .toList();
        response.setPriceMap(priceMaps);

        // 余票映射
        List<Map<String, Integer>> remainingMaps = transferResult.getSegments().stream()
                .map(TransferSegment::getRemainingMap)
                .toList();
        response.setRemainingTicketNumMap(remainingMaps);

        return response;
    }

    private void fillTrainSearchResult(List<TrainSearchResponseDTO> results,String date){
        List<Long> trainDOList = results.stream()
                .flatMap(e -> e.getSegments().stream())
                .map(TrainRoutePairDO::getTrainId)
                .toList();


        Map<Long,List<Integer>> seatTypemap=seatService.batchGetSeatTypes(trainDOList);

        //获取搜索结果中的所有列车的经过的站点map
        Map<Long,List<String>> stationsmap=trainStationService.batchGetStationNames(trainDOList);

        //获取搜索结果中的所有列车+各列车的种类余票map

        Map<String,List<Integer>> remainingTicketmap=ticketRemainingService.batchCalculateRemaining(
                trainDOList,
                date,
                seatTypemap,
                stationsmap
        );

        List<FareCalculationRequestDTO> fareRequests=new ArrayList<>();
        results.forEach(result-> result.getSegments().forEach(seg->{
            String startStation=seg.getDepartureStation();
            String endStation=seg.getArrivalStation();
            fareRequests.addAll(trainDOList.stream()
                    .flatMap(t->seatTypemap.get(t)
                            .stream()
                            .map(s->{
                                FareCalculationRequestDTO fareRequest = new FareCalculationRequestDTO();
                                fareRequest.setTrainId(t);
                                fareRequest.setDepartureStation(startStation);
                                fareRequest.setArrivalStation(endStation);
                                fareRequest.setSeatType(s);
                                // 默认成人票
                                fareRequest.setPassengerType(0);
                                return fareRequest;
                            })
                    )
                    .toList()
            );
        }));
        Map<CompositeKey4<Long,Integer,String,String>,BigDecimal> fareCalculationCachemap=new HashMap<>();
        List<FareCalculationResultDTO> fareCalculationResultDTOS=fareCalculationService.batchCalculateFare(fareRequests);
        for(FareCalculationResultDTO fareCalculationResultDTO:fareCalculationResultDTOS){
            fareCalculationCachemap.put(new CompositeKey4<>(
                    fareCalculationResultDTO.getTrainId(),
                    fareCalculationResultDTO.getSeatType(),
                    fareCalculationResultDTO.getDepartureStation(),
                    fareCalculationResultDTO.getArrivalStation()
            ),fareCalculationResultDTO.getTotalFare());
        }





        // 填充余票和票价信息
        results.forEach((result)->{
            int transferCount = result.getSegments().size();
            result.setTransferCount(transferCount);


            LocalDateTime firstDeparture = result.getSegments().get(0).getStartTime().atDate(LocalDate.now()); // 必须是 Date
            result.setFirstDepartureTime(DateUtils.format(firstDeparture,"HH:mm"));

            //所有火车线路的dayDiff之和
            int dayDiff=result.getSegments()
                    .stream()
                    .mapToInt(TrainRoutePairDO::getDayDiff)
                    .sum();
            LocalDateTime finalArrival = result.getSegments().get(transferCount - 1).getEndTime().atDate(LocalDate.now().plusDays(dayDiff)); // 必须是 Date 要算偏移
            result.setFinalArrivalTime(DateUtils.format(finalArrival,"HH:mm"));
            result.setTotalDurationMinutes(DateUtils.diffMinutes(firstDeparture,finalArrival));

            // 每段行程的列车的各个座位的价格
            List<Map<String, BigDecimal>> totalPriceBySeatType = new ArrayList<>();
            //每段行程的列车的各个座位的余票
            List<Map<String,Integer>> remainingTickets=new ArrayList<>();

            //获取车次
            //获取区间
            //获取座位类型
            //批量获取该区间与座位类型的余票
            result.getSegments().forEach((segment)->{
                //获取车次
                String trainNum=segment.getTrainNumber();
                Long trainId=segment.getTrainId();
                //获取座位类型
                List<Integer> seatTypes=seatTypemap.get(trainId);
                //获取站点
                List<String> stations=stationsmap.get(trainId);
                String startStation=segment.getDepartureStation();
                String endStation=segment.getArrivalStation();
                //获取区间
                int i=stations.indexOf(startStation);
                int j=stations.indexOf(endStation);

                //该段车次的各个座位种类的余票
                HashMap<String,Integer> remainingTicket=new HashMap<>();

                //判断这个区间是否再缓存存在
                seatTypes.forEach((seatType)->{
                    Integer count=remainingTicketmap
                            .get(trainId+"_"+seatType)
                            .stream()
                            .skip(i)
                            .limit(j-i)
                            .min(Integer::compareTo)
                            .orElse(0);
                    remainingTicket.put(SeatType.getDescByCode(seatType),count);
                });
                //填充该段余票
                remainingTickets.add(remainingTicket);

                //该段车次的各个座位种类的价格
                HashMap<String,BigDecimal> priceTicket=new HashMap<>();

                // 计算该段票价
                seatTypes.forEach((seatType)->{
                    BigDecimal fare = fareCalculationCachemap.get(new CompositeKey4<>(
                            trainId,
                            seatType,
                            startStation,
                            endStation
                    ));
                    priceTicket.put(SeatType.getDescByCode(seatType),fare);
                });
                totalPriceBySeatType.add(priceTicket);
            });

            // 设置余票
            result.setRemainingTicketNumMap(remainingTickets);

            // 设置票价
            result.setPriceMap(totalPriceBySeatType);
        });
    }
}

