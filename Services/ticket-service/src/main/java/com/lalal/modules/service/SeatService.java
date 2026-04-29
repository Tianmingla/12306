package com.lalal.modules.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lalal.modules.entity.SeatDO;

import java.util.List;
import java.util.Map;

public interface SeatService extends IService<SeatDO> {
    Map<Long, List<Integer>> batchGetSeatTypes(List<Long> tIds);
}
