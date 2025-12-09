package com.lalal.modules.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lalal.modules.entity.StationDO;
import com.lalal.modules.mapper.StationMapper;
import com.lalal.modules.service.StationService;
import org.springframework.stereotype.Service;

@Service
public class StationServiceImpl extends ServiceImpl<StationMapper, StationDO> implements StationService {
}
