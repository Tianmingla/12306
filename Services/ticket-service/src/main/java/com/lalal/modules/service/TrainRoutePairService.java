package com.lalal.modules.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lalal.modules.dto.response.TrainSearchResponseDTO;
import com.lalal.modules.entity.TrainRoutePairDO;

import java.util.List;

public interface TrainRoutePairService extends IService<TrainRoutePairDO> {
    /**
     * 根据城市搜索车次API，支持中转和热门中转方案
     */
    List<TrainSearchResponseDTO> searchTrains(String from, String mid, String to, String date);
}

