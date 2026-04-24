package com.lalal.modules.strategy.impl;

import com.lalal.modules.core.seat.SeatLayout;

import com.lalal.modules.dao.CarriageDO;
import com.lalal.modules.dto.request.SeatSelectionRequestDTO;
import com.lalal.modules.enumType.train.SeatType;
import com.lalal.modules.mapper.TrainStationMapper;
import com.lalal.modules.strategy.AbstractSeatSelectionStrategy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SoftSeatSelectionStrategy extends AbstractSeatSelectionStrategy {

    public SoftSeatSelectionStrategy(StringRedisTemplate redisTemplate, 
                                   DefaultRedisScript<String> seatSelectionScript,
                                   TrainStationMapper trainStationMapper) {
        super(redisTemplate, seatSelectionScript, trainStationMapper);
    }

    @Override
    protected List<int[]> generateCandidateGroups(CarriageDO carriage, SeatSelectionRequestDTO request) {
        int seatCount = carriage.getSeatCount();
        int numNeeded = request.getPassengers().size();
        SeatLayout layout = getLayout();
        int rows = seatCount / layout.getSeatsPerRow();
        
        List<int[]> candidates = new ArrayList<>();
        for (int i = 1; i <= rows; i++) {
            candidates.addAll(layout.getAdjacentGroups(i, numNeeded));
        }
        int mod=seatCount%layout.getSeatsPerRow();
        if(mod>0){
            candidates.addAll(layout.getModGroups(rows+1,numNeeded,mod));
        }
        return candidates;
    }

    @Override
    protected SeatLayout getLayout() {
        return SeatLayout.SOFT_SEAT;
    }

    @Override
    public int getSeatType() {
        return SeatType.SOFT_SEAT.getCode();
    }
}
