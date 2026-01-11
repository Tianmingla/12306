package com.lalal.modules.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lalal.framework.cache.SafeCacheTemplate;
import com.lalal.modules.constant.cache.CacheConstant;
import com.lalal.modules.core.seat.SeatTypeParser;
import com.lalal.modules.dao.*;
import com.lalal.modules.dto.request.SeatSelectionRequestDTO;
import com.lalal.modules.dto.response.TicketDTO;
import com.lalal.modules.mapper.*;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@AllArgsConstructor
public class SeatSelectionServiceImpl implements SeatSelectionService{
    SafeCacheTemplate safeCacheTemplate;
    TrainMapper trainMapper;
    CarriageMapper carriageMapper;
    TicketMapper ticketMapper;
    SeatMapper seatMapper;
    TrainStationMapper trainStationMapper;
    SeatTypeParser parser;

    @Override
    public TicketDTO select(SeatSelectionRequestDTO seatSelectionRequest) {



        return null;
    }
}
