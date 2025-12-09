package com.lalal.modules.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lalal.modules.entity.TrainStationDO;
import com.lalal.modules.mapper.TrainStationMapper;
import com.lalal.modules.service.TrainStationService;
import org.springframework.stereotype.Service;

@Service
public class TrainStationServiceImpl extends ServiceImpl<TrainStationMapper, TrainStationDO> implements TrainStationService {
}
