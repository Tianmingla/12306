package com.lalal.modules.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lalal.modules.entity.RegionDO;
import com.lalal.modules.mapper.RegionMapper;
import com.lalal.modules.service.RegionService;
import org.springframework.stereotype.Service;

@Service
public class RegionServiceImpl extends ServiceImpl<RegionMapper, RegionDO> implements RegionService {
}
