package com.lalal.modules.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lalal.modules.entity.TrainStationDO;

import java.util.List;

public interface TrainStationService extends IService<TrainStationDO> {
    List<String> getStationNamesByTrainNum(String trainNum);
    List<List<String>> getStationNamesByTrainIds(List<Long> trainIds);
}
