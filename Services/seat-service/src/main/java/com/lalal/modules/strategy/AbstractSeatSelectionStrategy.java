package com.lalal.modules.strategy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.lalal.framework.cache.SafeCacheTemplate;
import com.lalal.modules.constant.cache.CacheConstant;
import com.lalal.modules.core.seat.SeatLayout;
import com.lalal.modules.dao.CarriageDO;
import com.lalal.modules.dao.TrainStationDO;
import com.lalal.modules.dto.request.SeatSelectionRequestDTO;
import com.lalal.modules.dto.response.TicketDTO;
import com.lalal.modules.mapper.TrainStationMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public abstract class AbstractSeatSelectionStrategy implements SeatSelectionStrategy {
    protected final StringRedisTemplate redisTemplate;
    protected final DefaultRedisScript<String> seatSelectionScript;
    protected final TrainStationMapper trainStationMapper;
    @Autowired
    protected SafeCacheTemplate safeCacheTemplate;

    protected AbstractSeatSelectionStrategy(StringRedisTemplate redisTemplate, DefaultRedisScript<String> seatSelectionScript, TrainStationMapper trainStationMapper) {
        this.redisTemplate = redisTemplate;
        this.seatSelectionScript = seatSelectionScript;
        this.trainStationMapper = trainStationMapper;
    }


    @Override
    public TicketDTO select(SeatSelectionRequestDTO request, List<CarriageDO> carriages) {
        // 1. 获取区间信息
        List<String> stations=safeCacheTemplate.safeGet(
                CacheConstant.trainStation(carriages.get(0).getTrainId()),
                () -> trainStationMapper.selectList(new LambdaQueryWrapper<TrainStationDO>()
                        .select(TrainStationDO::getStationName)
                                //.eq(TrainStationDO::getRunDate, request.getDate())
                        .eq(TrainStationDO::getTrainNumber, request.getTrainNum())
                                .orderByAsc(TrainStationDO::getSequence))
                        .stream()
                        .map(TrainStationDO::getStationName)
                        .toList(),
                new TypeReference<List<String>>() {},
                3,
                TimeUnit.DAYS
        );

        int startSeq = -1;
        int endSeq = -1;
        for (int i=0;i<stations.size();i++) {
            if (stations.get(i).equals(request.getStartStation())) {
                startSeq = i;
            }
            if (stations.get(i).equals(request.getEndStation())) {
                endSeq = i;
            }
        }
        
        if (startSeq == -1 || endSeq == -1 || startSeq >= endSeq) {
            return null;
        }

        // 0-based segment indices
        int startSeg = startSeq - 1;
        int endSeg = endSeq - 2; // A-B is segment 0 (seq 1 to 2)
        int totalSegs = stations.size() - 1;

        // 2. 遍历车厢尝试选座
        for (CarriageDO carriage : carriages) {
            List<int[]> groups = generateCandidateGroups(carriage, request);
            if (groups.isEmpty()) continue;

            String groupsStr = groups.stream()
                    .map(group -> {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < group.length; i++) {
                            sb.append(group[i]);
                            if (i < group.length - 1) sb.append(",");
                        }
                        return sb.toString();
                    })
                    .collect(Collectors.joining(";"));

            String detailKey = CacheConstant.trainTicketDetailKey(carriage.getTrainId(), request.getDate(), carriage.getCarriageNumber());
            String remainingKey = CacheConstant.trainTicketRemainingKey(carriage.getTrainId(), request.getDate(), getSeatType());

            // 调用 Lua 脚本
            String result = redisTemplate.execute(seatSelectionScript,
                    List.of(detailKey, remainingKey),
                    String.valueOf(startSeg),
                    String.valueOf(endSeg),
                    String.valueOf(request.getPassengers().size()),
                    groupsStr,
                    String.valueOf(totalSegs)
            );

            if (result != null) {
                // 选座成功，构建返回结果
                String[] selectedIndices = result.split(",");
                List<TicketDTO.TicketItem> items = new ArrayList<>();
                SeatLayout layout = getLayout();
                for (int i = 0; i < selectedIndices.length; i++) {
                    int idx = Integer.parseInt(selectedIndices[i]);
                    long passengerId = request.getPassengers().get(i).getId();
                    items.add(new TicketDTO.TicketItem(passengerId, layout.getSeatNumber(idx), getSeatType(), carriage.getCarriageNumber()));
                }
                
                TicketDTO ticketDTO = new TicketDTO();
                ticketDTO.setAccount(request.getAccount());
                ticketDTO.setTrainNum(request.getTrainNum());
                ticketDTO.setStartStation(request.getStartStation());
                ticketDTO.setEndStation(request.getEndStation());
                ticketDTO.setDate(request.getDate());
                ticketDTO.setItems(items);
                return ticketDTO;
            }
        }

        return null;
    }

    protected abstract List<int[]> generateCandidateGroups(CarriageDO carriage, SeatSelectionRequestDTO request);


    protected abstract SeatLayout getLayout();
}
