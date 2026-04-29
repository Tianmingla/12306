package com.lalal.modules.service;


import com.baomidou.mybatisplus.extension.service.IService;
import com.lalal.modules.entity.TrainStationDO;
import com.lalal.modules.dto.response.TrainStationDetailRespDTO;

import java.util.List;
import java.util.Map;

public interface TrainStationService extends IService<TrainStationDO> {
    List<TrainStationDetailRespDTO> getStationDetailsByTrainNum(String trainNum);
    List<String> getStationNamesByTrainNum(String trainNum);
    List<List<String>> getStationNamesByTrainIds(List<Long> trainIds);

    Map<Long, List<String>> batchGetStationNames(List<Long> trainDOList);
}
