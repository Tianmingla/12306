package com.lalal.modules.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lalal.modules.entity.CarriageDO;
import com.lalal.modules.mapper.CarriageMapper;
import com.lalal.modules.service.CarriageService;
import org.springframework.stereotype.Service;

@Service
public class CarriageServiceImpl extends ServiceImpl<CarriageMapper, CarriageDO> implements CarriageService {
}
