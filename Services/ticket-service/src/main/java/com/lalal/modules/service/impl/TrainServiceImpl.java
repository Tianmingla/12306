package com.lalal.modules.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lalal.modules.entity.TrainDO;
import com.lalal.modules.mapper.TrainMapper;
import com.lalal.modules.service.TrainService;
import org.springframework.stereotype.Service;

@Service
public class TrainServiceImpl extends ServiceImpl<TrainMapper, TrainDO> implements TrainService {
}
