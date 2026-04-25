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
import com.lalal.modules.enumType.train.SeatType;
import com.lalal.modules.mapper.*;
import com.lalal.modules.service.FareCalculationService;
import com.lalal.modules.service.StationService;
import com.lalal.modules.service.TrainRoutePairService;
import com.lalal.modules.service.TrainStationService;
import com.lalal.modules.utils.DateUtils;
import lombok.AllArgsConstructor;
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


@Service
@AllArgsConstructor
public class TrainRoutePairServiceImpl extends ServiceImpl<TrainRoutePairMapper, TrainRoutePairDO> implements TrainRoutePairService {


    private final StationServiceImpl stationServiceImpl;
    StationService stationService;
    SeatMapper seatMapper;
    TicketMapper ticketMapper;
    TrainRoutePairMapper trainRoutePairMapper;
    SafeCacheTemplate safeCacheTemplate;
    TrainStationService trainStationService;
    RedissonClient redissonClient;
    FareCalculationService fareCalculationService;
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
                direct = direct.stream().filter(d -> d.getStartTime().isAfter(LocalTime.now())).toList();
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
        // 3. 无直达，返回热门中转方案（mock热门站台）
        List<String> hotMidStationNames = Arrays.asList("长沙", "广州", "北京", "上海"); // TODO: 热门中转站统计优化
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
        List<Long> trainDOList = results.stream()
                .flatMap(e -> e.getSegments().stream())
                .map(TrainRoutePairDO::getTrainId)
                .toList();

        //获取所有火车的座位种类map
        List<String> seatTypeKeys=trainDOList.stream()
                .map(CacheConstant::trainSeatType)
                .toList();
        List<Object[]> seatTypeArgs=trainDOList.stream()
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
        for (int i=0;i<trainDOList.size();i++){
            seatTypemap.put(trainDOList.get(i),seatTypeList.get(i));
        }

        //获取搜索结果中的所有列车的经过的站点map
        List<List<String>> stationsList=trainStationService.getStationNamesByTrainIds(trainDOList);
        Map<Long,List<String>> stationsmap=new HashMap<>();
        for (int i=0;i<trainDOList.size();i++){
            stationsmap.put(trainDOList.get(i),stationsList.get(i));
        }

        //获取搜索结果中的所有列车+各列车的种类余票map
        List<String> remainingTicketKeys=trainDOList.stream()
                .flatMap((t)->
                    seatTypemap.get(t).stream()
                            .map(s->CacheConstant.trainTicketRemainingKey(
                                    t,
                                    date,
                                    s
                            ))
                )
                .toList();
        List<Object[]> remainingTicketArgs=trainDOList.stream()
                .flatMap((t)->
                        seatTypemap.get(t)
                                .stream()
                                .map(s->new Object[]{t,s})
                )
                .toList();
        Map<String,Integer> remainingTicketIndex=new HashMap<>();
        int recordIdx=0;
        for(int i=0;i<trainDOList.size();i++){
            List<Integer> seatTypes=seatTypemap.get(trainDOList.get(i));
            for(int j=0;j<seatTypes.size();j++){
                remainingTicketIndex.put(
                        trainDOList.get(i)+"_"+seatTypes.get(j),
                        recordIdx++
                );
            }
        }
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
                        for(int i=0;i<stationsmap.get(trainId).size()-1;i++) {
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
                    FareCalculationRequestDTO fareRequest = new FareCalculationRequestDTO();
                    fareRequest.setTrainId(trainId);
                    fareRequest.setTrainNumber(trainNum);
                    fareRequest.setDepartureStation(startStation);
                    fareRequest.setArrivalStation(endStation);
                    fareRequest.setSeatType(seatType);
                    fareRequest.setPassengerType(0); // 默认成人票

                    try {
                        FareCalculationResultDTO fareResult = fareCalculationService.calculateFare(fareRequest);
                        BigDecimal fare = fareResult.getTotalFare();
                        priceTicket.put(SeatType.getDescByCode(seatType),fare);
                    } catch (Exception e) {
                        // 票价计算失败时使用默认值
                        priceTicket.put(SeatType.getDescByCode(seatType),BigDecimal.ZERO);
                    }
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

