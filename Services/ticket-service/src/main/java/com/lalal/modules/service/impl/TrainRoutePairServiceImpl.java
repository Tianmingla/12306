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
        //TODO 这里可能重复车次 需要去重
        List<TrainRoutePairDO> trainDOList = results.stream()
                .flatMap(e -> e.getSegments().stream())
                .toList();

        //获取所有火车的座位种类map
        List<String> seatTypeKeys=trainDOList.stream()
                .map(t->CacheConstant.trainSeatType(t.getTrainNumber()))
                .toList();
        List<Object[]> seatTypeArgs=trainDOList.stream()
                .map(t-> new Object[]{t.getTrainId()})
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
                seatTypeArgs,
                3,
                TimeUnit.DAYS
        );
        Map<Long,List<Integer>> seatTypemap=new HashMap<>();
        for (int i=0;i<trainDOList.size();i++){
            seatTypemap.put(trainDOList.get(i).getTrainId(),seatTypeList.get(i));
        }

        //获取搜索结果中的所有列车的经过的站点map
        List<String> stationsKeys=trainDOList.stream()
                .map(t->CacheConstant.trainStation(t.getTrainNumber()))
                .toList();
        //性能考虑 直接用s
//        List<Object[]> stationsArgs=trainDOList.stream()
//                .map(t-> new Object[]{t.getTrainId()})
//                .toList();
        List<List<String>> stationsList=safeCacheTemplate.safeBatchGet(
                stationsKeys,
                (List<Object[]> args)->{
                    List<Long> trainIds=args.stream()
                            .map(arg->(Long)arg[0])
                            .toList();
                    Map<Long,Integer> indexmap=new HashMap<>();
                    List<List<String>> result=new ArrayList<>(args.size());
                    for(int i=0;i<trainIds.size();i++){
                        indexmap.put(trainIds.get(i),i);
                        result.add(new ArrayList<>());
                    }
                    LambdaQueryWrapper<TrainStationDO> lambdaQueryWrapper=new LambdaQueryWrapper<TrainStationDO>()
                            .select(TrainStationDO::getStationName,TrainStationDO::getTrainId)
                            .in(TrainStationDO::getTrainId,trainIds)
                            .orderByAsc(TrainStationDO::getTrainId,TrainStationDO::getSequence);
                    List<Map<String,Object>> objects=trainStationMapper.selectMaps(lambdaQueryWrapper);
                    for (Map<String,Object> objectMap:objects){
                        result.get(indexmap.get(objectMap.get("train_id"))).add((String)objectMap.get("station_name"));
                    }
                    return result;
//
                },
                seatTypeArgs,
                3,
                TimeUnit.DAYS
        );
        Map<Long,List<String>> stationsmap=new HashMap<>();
        for (int i=0;i<trainDOList.size();i++){
            stationsmap.put(trainDOList.get(i).getTrainId(),stationsList.get(i));
        }

        //获取搜索结果中的所有列车+各列车的种类余票map
        List<String> remainingTicketKeys=trainDOList.stream()
                .flatMap((t)->
                    seatTypemap.get(t.getTrainId()).stream()
                            .map(s->CacheConstant.trainTicketRemainingKey(
                                    t.getTrainNumber(),
                                    date,
                                    s
                            ))
                )
                .toList();
        List<Object[]> remainingTicketArgs=trainDOList.stream()
                .flatMap((t)->
                        seatTypemap.get(t.getTrainId())
                                .stream()
                                .map(s->new Object[]{t.getTrainId(),s})
                )
                .toList();
        Map<String,Integer> remainingTicketIndex=new HashMap<>();
        int recordIdx=0;
        for(int i=0;i<trainDOList.size();i++){
            List<Integer> seatTypes=seatTypemap.get(trainDOList.get(i).getTrainId());
            for(int j=0;j<seatTypes.size();j++){
                remainingTicketIndex.put(
                        trainDOList.get(i).getTrainId()+"_"+seatTypes.get(j),
                        recordIdx++
                );
            }
        }
        List<List<Integer>> remainingTicketList=safeCacheTemplate.safeBatchGet(
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
                    QueryWrapper wrapper=new QueryWrapper<SeatDO>()
                            .select("train_id","seat_type","count(*) as count")
                            .in("train_id",trainIds)
                            .in("seat_type",seatTypes)
                            .groupBy("train_id","seat_type");
                    List<Map<String,Object>> objs=seatMapper.selectMaps(wrapper);
                    //TODO 数据库索引
                    QueryWrapper<TicketDO> wrapper1 = new QueryWrapper<>();
                    wrapper1.select("train_id","seat_type", "departure_station", "arrival_station", "COUNT(*) AS count")
                            .in("train_id", trainIds)
                            .in("seat_type", seatTypes)
                            .eq("travel_date", date)
                            .groupBy("train_id", "seat_type", "departure_station", "arrival_station");

                    List<Map<String,Object>> objs1=ticketMapper.selectMaps(wrapper1);
                    for(Map<String,Object> e:objs) {
                        String key=e.get("train_id")+"_"+e.get("seat_type");
                        Integer idx=indexmap.get(key);
                        int size=stationsmap.get(e.get("train_id")).size()-1;
                        List<Integer> list=new ArrayList<>(size);
                        for(int i=0;i<size;i++){
                            list.add(((Long)e.get("count")).intValue());
                        }
                        result.set(idx,list);

                    }
                    for(Map<String,Object> e:objs1) {
                        String key=e.get("train_id")+"_"+e.get("seat_type");
                        Integer idx=indexmap.get(key);

                        List<Integer> list=result.get(idx);
                        List<String> stationNameList=stationsmap.get(e.get("train_id"));
                        Integer i=stationNameList.indexOf(e.get("departure_station"));
                        //太麻烦了 这就是java的包装 我又不能用普通数组 不然redis序列化又出问题
                        //如果去一步一步为了性能去重写java生态库 保证理论上的java上限 不如换语言
                        //TODO ticket待定 还没确定买区间A-C是存A-B B-C还是只存A-C
                        list.set(i,list.get(i)-((Long)e.get("count")).intValue());
                    }
                    return result;
                },
                remainingTicketArgs,
                3,
                TimeUnit.DAYS
        );
        Map<String,List<Integer>> remainingTicketmap=new HashMap<>();
        for(Map.Entry<String,Integer> idx:remainingTicketIndex.entrySet()){
            remainingTicketmap.put(idx.getKey(),remainingTicketList.get(idx.getValue()));
        }




        // TODO:  填充价格等信息
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
                Long trainId=segment.getTrainId();
                //获取座位类型
                List<Integer> seatTypes=seatTypemap.get(trainId);
                //获取站点
                List<String> stations=stationsmap.get(trainId);
                String startStation=segment.getDepartureStation();
                String endStation=segment.getArrivalStation();
                //获取区间
                int i=stations.indexOf(startStation);
                int j=stations.indexOf(endStation)+1;
                HashMap<Integer,List<Integer>> remainingTickets=new HashMap<>();
                //判断这个区间是否再缓存存在
                seatTypes.forEach((seatType)->{
                    List<Integer> counts=remainingTicketmap
                            .get(trainId+"_"+seatType)
                            .stream()
                            .skip(i)
                            .limit(j-i)
                            .toList();
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

