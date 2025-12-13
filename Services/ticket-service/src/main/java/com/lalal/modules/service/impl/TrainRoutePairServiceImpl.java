package com.lalal.modules.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lalal.framework.cache.SafeCacheTemplate;
import com.lalal.modules.constant.cache.CacheConstant;
import com.lalal.modules.dto.response.TrainSearchResponseDTO;
import com.lalal.modules.entity.*;
import com.lalal.modules.enumType.train.SeatType;
import com.lalal.modules.mapper.*;
import com.lalal.modules.service.StationService;
import com.lalal.modules.service.TrainRoutePairService;
import com.lalal.modules.utils.DateUtils;
import lombok.AllArgsConstructor;
import org.apache.ibatis.cache.Cache;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


@Service
@AllArgsConstructor
public class TrainRoutePairServiceImpl extends ServiceImpl<TrainRoutePairMapper, TrainRoutePairDO> implements TrainRoutePairService {


    private final StationServiceImpl stationServiceImpl;
    StationService stationService;
    SeatMapper seatMapper;
    TicketMapper ticketMapper;
    TrainRoutePairMapper trainRoutePairMapper;
    SafeCacheTemplate safeCacheTemplate;
    TrainStationMapper trainStationMapper;
    RedissonClient redissonClient;
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
                3,
                TimeUnit.DAYS
        );
        if (!direct.isEmpty()) {
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
        // 3. 无直达，返回热门中转方案（mock热门站台）
        List<String> hotMidStationNames = Arrays.asList("南京南", "郑州东", "武汉", "长沙南"); // TODO: 热门中转站统计优化
        List<StationDO> hotMidStations=hotMidStationNames
                .stream()
                .map((e)->{
                    StationDO stationDO=new StationDO();
                    stationDO.setName(e);
                    return stationDO;
                })
                .toList();



        List<TrainSearchResponseDTO> result=handleMidMerge(from,mid,to);

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
                3,
                TimeUnit.DAYS
        );
        //合并结果
        List<TrainSearchResponseDTO> result=new ArrayList<>();
        for(int i=0;i<firstLeg.size();i++){
            for(int j=0;j<secondLeg.size();j++){
                //保证结果是同站换乘
                if(firstLeg.get(i).getArrivalStation()==secondLeg.get(j).getDepartureStation()) {
                    TrainSearchResponseDTO trainSearchResponseDTO = new TrainSearchResponseDTO();
                    trainSearchResponseDTO.setSegments(List.of(firstLeg.get(i), secondLeg.get(j)));
                    trainSearchResponseDTO.setTransferCount(2);
                    result.add(trainSearchResponseDTO);
                }
            }
        }
        return result;
    }
    private void fillTrainSearchResult(List<TrainSearchResponseDTO> results,String date){
        // TODO:  填充余票和价格等信息
        results.forEach((result)->{
            int transferCount = result.getSegments().size();
            result.setTransferCount(transferCount);

            Date firstDeparture = result.getSegments().get(0).getStartTime(); // 必须是 Date
            result.setFirstDepartureTime(DateUtils.format(firstDeparture,"HH:mm"));
            Date finalArrival = result.getSegments().get(transferCount - 1).getEndTime(); // 必须是 Date
            result.setFinalArrivalTime(DateUtils.format(finalArrival,"HH:mm"));
            result.setTotalDurationMinutes(DateUtils.diffMinutes(firstDeparture,finalArrival));

            Map<Integer,List<Integer>> tickets=new HashMap<>();
            //获取车次
            //获取区间
            //获取座位类型
            //批量获取该区间与座位类型的余票
            result.getSegments().forEach((segment)->{
                //获取车次
                String trainNum=segment.getTrainNumber();
                String seatTypeCacheKey=CacheConstant.trainSeatType(trainNum);
                //获取座位类型
                List<Integer> seatTypes=safeCacheTemplate.safeGet(
                        seatTypeCacheKey,
                        ()->{
                            LambdaQueryWrapper<SeatDO> lambdaQueryWrapper=new LambdaQueryWrapper<SeatDO>()
                                    .select(SeatDO::getSeatType)
                                    .eq(SeatDO::getTrainId,segment.getTrainId())
                                    .groupBy(SeatDO::getSeatType);
                            return seatMapper.selectObjs(lambdaQueryWrapper)
                                    .stream()
                                    .map((e)->(Integer)e)
                                    .collect(Collectors.toCollection(ArrayList::new));
                        },
                        1,
                        TimeUnit.DAYS
                );
                String trainStationCacheKey= CacheConstant.trainStation(trainNum);
                List<String> stations=safeCacheTemplate.safeGet(
                        trainStationCacheKey,
                        ()->{
                            LambdaQueryWrapper<TrainStationDO> wrapper=new LambdaQueryWrapper<TrainStationDO>()
                                    .select(TrainStationDO::getStationName)
                                    .eq(TrainStationDO::getTrainNumber,trainNum)
                                    .orderByAsc(TrainStationDO::getSequence);
                            return trainStationMapper.selectObjs(wrapper)
                                    .stream()
                                    .map((obj)-> (String)obj)
                                    .collect(Collectors.toCollection(ArrayList::new));
                        },
                        1,
                        TimeUnit.DAYS
                );
                String startStation=segment.getDepartureStation();
                String endStation=segment.getArrivalStation();
                //获取区间
                int i=stations.indexOf(startStation);
                int j=stations.indexOf(endStation)+1;
                HashMap<Integer,List<Integer>> remainingTickets=new HashMap<>();
                //判断这个区间是否再缓存存在
                seatTypes.forEach((seatType)->{
                    String maxIntervalKes=CacheConstant.trainTicketRemainingKey(
                            trainNum,
                            date,
                            seatType
                    );
                    List<Integer> counts=safeCacheTemplate.safeGet(
                            maxIntervalKes,
                            ()->{
                                List<Integer> res=new ArrayList<>();
                                for(int k=0;k<stations.size()-1;k++){
                                    LambdaQueryWrapper wrapper=new LambdaQueryWrapper<SeatDO>()
                                            .eq(SeatDO::getTrainId,segment.getTrainId())
                                            .eq(SeatDO::getSeatType,seatType);
                                    Long count=seatMapper.selectCount(wrapper);
                                    //TODO 数据库索引
                                    LambdaQueryWrapper wrapper1=new LambdaQueryWrapper<TicketDO>()
                                            .eq(TicketDO::getTrainId,segment.getTrainId())
                                            .eq(TicketDO::getTravelDate,date)
                                            .eq(TicketDO::getSeatType,seatType)
                                            .eq(TicketDO::getDepartureStation,stations.get(k))
                                            .eq(TicketDO::getArrivalStation,stations.get(k+1));
                                    Long saleTickets=ticketMapper.selectCount(wrapper1);
                                    res.add((int)(count-saleTickets));
                                }
                                return res;
                            },
                            2,
                            TimeUnit.DAYS

                    ).stream().skip(i).limit(j-i).toList();
                    //TODO 多趟列车余票计算数据结构待定
                    remainingTickets.put(seatType,counts);
                    tickets.put(seatType,counts);
                });
            });
            for(Map.Entry<Integer, List<Integer>> ticket:tickets.entrySet()){
                int minVal = ticket.getValue()
                        .stream()
                        .min(Integer::compareTo)
                        .orElse(0);
                result.getRemainingTicketNumMap()
                        .put(SeatType.getDescByCode(ticket.getKey()), minVal);
            }
        });
    }

}

