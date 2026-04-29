package com.lalal.modules.service;

import com.lalal.modules.dto.FareCalculationRequestDTO;
import com.lalal.modules.dto.FareCalculationResultDTO;
import com.lalal.modules.template.CompositeKey3;

import java.util.List;
import java.util.Map;

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
     * 批量获取站间距离
     *
     * @param trainIds          列车ID
     * @param departureStations 出发站名称
     * @param arrivalStations   到达站名称
     * @return key:trainId_departureStation_arrivalStation value:站点距离
     */
    Map<String,Integer> batchGetDistance(List<Long> trainIds, List<String> departureStations, List<String> arrivalStations);
    /**
     * 批量获取站间距离
     *
     * @param trainIds          列车ID
     * @param departureStations 出发站名称
     * @param arrivalStations   到达站名称
     * @return key:CompositeKey3<Long,String,String> value:站点距离
     */
    Map<CompositeKey3<Long,String,String>,Integer> batchGetDistanceByCompositeKey3(List<Long> trainIds, List<String> departureStations, List<String> arrivalStations);

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
