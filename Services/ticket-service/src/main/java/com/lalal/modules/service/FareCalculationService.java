package com.lalal.modules.service;

import com.lalal.modules.dto.FareCalculationRequestDTO;
import com.lalal.modules.dto.FareCalculationResultDTO;

import java.util.List;

/**
 * 票价计算服务接口
 */
public interface FareCalculationService {

    /**
     * 计算票价
     *
     * @param request 票价计算请求
     * @return 票价计算结果
     */
    FareCalculationResultDTO calculateFare(FareCalculationRequestDTO request);

    /**
     * 批量计算票价
     *
     * @param requests 票价计算请求列表
     * @return 票价计算结果列表
     */
    List<FareCalculationResultDTO> batchCalculateFare(List<FareCalculationRequestDTO> requests);

    /**
     * 获取站间距离
     *
     * @param trainId          列车ID
     * @param departureStation 出发站名称
     * @param arrivalStation   到达站名称
     * @return 里程(公里)
     */
    Integer getDistance(Long trainId, String departureStation, String arrivalStation);

    /**
     * 获取站间距离（通过车次号）
     *
     * @param trainNumber      车次号
     * @param departureStation 出发站名称
     * @param arrivalStation   到达站名称
     * @return 里程(公里)
     */
    Integer getDistanceByTrainNumber(String trainNumber, String departureStation, String arrivalStation);
}
