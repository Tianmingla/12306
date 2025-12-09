package com.lalal.modules.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lalal.modules.entity.SeatDO;
import com.lalal.modules.mapper.SeatMapper;
import com.lalal.modules.service.SeatService;
import org.springframework.stereotype.Service;

@Service
public class SeatServiceImpl extends ServiceImpl<SeatMapper, SeatDO> implements SeatService {
}
