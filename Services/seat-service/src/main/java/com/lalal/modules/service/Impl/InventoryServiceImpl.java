package com.lalal.modules.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.lalal.framework.cache.SafeCacheTemplate;
import com.lalal.modules.constant.cache.CacheConstant;
import com.lalal.modules.core.context.AllocationContext;
import com.lalal.modules.core.seat.SeatTypeParser;
import com.lalal.modules.dao.SeatDO;
import com.lalal.modules.dao.TicketDO;
import com.lalal.modules.mapper.SeatMapper;
import com.lalal.modules.mapper.TicketMapper;
import com.lalal.modules.model.*;
import com.lalal.modules.model.Serialization.BooleanMaskSerializer;
import com.lalal.modules.service.InventoryService;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.scripting.ScriptSource;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;


@Service
public class InventoryServiceImpl implements InventoryService {
    @Autowired
    SafeCacheTemplate safeCacheTemplate;
    @Autowired
    TicketMapper ticketMapper;
    @Autowired
    SeatMapper seatMapper;
    @Autowired
    SeatTypeParser parser;
    @Autowired
    BooleanMaskSerializer serializer;

    String savingSeatLuaScript; //指定座位 抢座
    String selectSeatLuaScript;//脚本抢座

    InventoryServiceImpl(ResourceLoader resourceLoader) throws IOException {
        Resource resource = resourceLoader.getResource("classpath:lua/savingSeat.lua");
        Resource resource1 = resourceLoader.getResource("classpath:lua/selectSeat.lua");
        try (InputStream is = resource.getInputStream()) {
            this.savingSeatLuaScript = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (InputStream is = resource1.getInputStream()) {
            this.selectSeatLuaScript = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }


    @Override
    public SeatInventory loadInventory(Train train, String date, String start, String end) {

        Long trainId=train.getId();
        List<String> stationList=train.getStations();
        List<String> ticketDetailCacheKeys=train.getCarriages().stream()
                .map(c-> CacheConstant.trainTicketDetailKey(train.getId(), date,c.getCarNumber()))
                .toList();
        List<Object[]> carriageArgs=train.getCarriages().stream()
                .map(c->new Object[]{c.getCarNumber()})
                .toList();
        safeCacheTemplate.setCustomizedSerializer(serializer);
        List<List<BooleanMask>> ticketDetails=safeCacheTemplate.safeBatchLGet(
                ticketDetailCacheKeys,
                (args)->{
                    List<String> carriageNums=args.stream()
                            .map(a->(String)a[0])
                            .toList();
                    Map<String,Integer> indexmap=new HashMap<>();
                    List<List<BooleanMask>> result=new ArrayList<>();

                    LambdaQueryWrapper<TicketDO> lambdaQueryWrapper=new LambdaQueryWrapper<>();
                    lambdaQueryWrapper
                            .select(TicketDO::getCarriageNumber,TicketDO::getSeatNumber,TicketDO::getDepartureStation,TicketDO::getArrivalStation)
                            .eq(TicketDO::getId,trainId)
                            .eq(TicketDO::getTravelDate,date)
                            .in(TicketDO::getCarriageNumber,carriageNums);
                    List<TicketDO> tickets=ticketMapper.selectList(lambdaQueryWrapper);

                    QueryWrapper<SeatDO> queryWrapper=new QueryWrapper<>();
                    queryWrapper
                            .select("carriage_number","count(*) as count")
                            .in("carriage_number",carriageNums)
                            .eq("train_id",trainId)
                            .groupBy("carriage_number");
                    List<Map<String,Object>> objs=seatMapper.selectMaps(queryWrapper);
                    Map<String,Integer> initValue=new HashMap<>();
                    for(Map<String,Object> obj:objs){
                        initValue.put((String) obj.get("carriage_number"),((Long)obj.get("count")).intValue());
                    }
                    for(int i=0;i<args.size();i++){
                        indexmap.put(carriageNums.get(i),i);
                        List<BooleanMask> list=new ArrayList<>();
                        for(int j=0;j<stationList.size()-1;j++){
                            list.add(new BooleanMask(initValue.get(carriageNums.get(i))));
                        }
                        result.add(list);
                    }
                    for(TicketDO ticket:tickets){
                        String seatNumber=ticket.getSeatNumber();
                        String carriageNumber=ticket.getCarriageNumber();
                        int i= train.stationIndex(ticket.getDepartureStation());
                        int j= train.stationIndex(ticket.getArrivalStation());
                        List<BooleanMask> list=result.get(indexmap.get(carriageNumber));
                        int k=parser.index(train.getTypeByCarriageNum(carriageNumber).getDesc(),seatNumber);

                        for(;i<j;i++){
                            list.get(i).set(k,true);
                        }
                    }
                    return result;
                },
                new TypeReference<BooleanMask>(){},
                carriageArgs,
                3,
                TimeUnit.DAYS
        );
        safeCacheTemplate.clearContext();
        SeatInventory seatInventory=new SeatInventory(train,start,end);
        List<Carriage> carriages=train.getCarriages();
        for(int i=0;i<carriages.size();i++){
            seatInventory.loadCarriageMask(carriages.get(i).getIndex(),ticketDetails.get(i));
        }


        return seatInventory;
    }

    @Override
    public boolean commit(AllocationContext ctx) {

        RedisTemplate redisTemplate=safeCacheTemplate.instance();
        RedisSerializer keySerializer=redisTemplate.getKeySerializer();

        List<SeatLocation> seatLocations=ctx.getResult().entrySet().stream().map(Map.Entry::getValue).toList();
        Long trainId=ctx.getTrain().getId();
        String date=ctx.getRequest().getDate();
        Train train=ctx.getTrain();
        Integer startIdx=train.stationIndex(ctx.getInventory().getStartStation());
        Integer endIdx=train.stationIndex(ctx.getInventory().getEndStation())-1;


        List<Boolean> result= (List<Boolean>) redisTemplate.execute((RedisCallback<List<Boolean>>)(connection)->{
            List<Boolean> res=new ArrayList<>();
            for(SeatLocation seatLocation:seatLocations) {

                byte[] keyBytes = keySerializer.serialize(CacheConstant.trainTicketDetailKey(trainId, date, seatLocation.getCarNo()));
                byte[] startIdxBytes= String.valueOf(startIdx).getBytes(StandardCharsets.UTF_8);
                byte[] endIdxBytes= String.valueOf(endIdx).getBytes(StandardCharsets.UTF_8);
                byte[] seatIdxBytes=String.valueOf(seatLocation.getSeatIndex()).getBytes(StandardCharsets.UTF_8);

                Object evalResult=connection.scriptingCommands().eval(savingSeatLuaScript.getBytes(StandardCharsets.UTF_8), ReturnType.BOOLEAN, 1, keyBytes,startIdxBytes,endIdxBytes,seatIdxBytes);
                res.add((Boolean) evalResult);
            }

            return res;
        });


        return result.stream()
                .allMatch(r -> r instanceof Boolean && (Boolean) r == Boolean.TRUE);
    }

    @Override
    public List<Integer> selectSeat(AllocationContext ctx) {
        RedisTemplate redisTemplate=safeCacheTemplate.instance();
        RedisSerializer keySerializer=redisTemplate.getKeySerializer();

        Long trainId=ctx.getTrain().getId();
        String date=ctx.getRequest().getDate();
        Train train=ctx.getTrain();
        Integer startIdx=train.stationIndex(ctx.getInventory().getStartStation());
        Integer endIdx=train.stationIndex(ctx.getInventory().getEndStation())-1;
        PassengerGroup passengerGroup=ctx.getPassengerGroup();

        List<Integer> results= redisTemplate.execute((RedisCallback<List<Integer>>) connection->{
            List<Integer> evalResults=new ArrayList<>();
            for(Passenger passenger:passengerGroup.getMembers()) {
                byte[] keyBytes = keySerializer.serialize(CacheConstant.trainTicketDetailKey(trainId, date, seatLocation.getCarNo()));
                byte[] startIdxBytes= String.valueOf(startIdx).getBytes(StandardCharsets.UTF_8);
                byte[] endIdxBytes= String.valueOf(endIdx).getBytes(StandardCharsets.UTF_8);
                Object evalResult=connection.scriptingCommands().eval(selectSeatLuaScript.getBytes(StandardCharsets.UTF_8), ReturnType.INTEGER, 1, keyBytes,startIdxBytes,endIdxBytes);
                evalResults.add((Integer) evalResult);
            }

            return evalResults;
        });
        for(Passenger passenger:passengerGroup.getMembers()) {
//            ctx.addMatch(passenger,train.getCarriage().);
        }

        return null;
    }


}
