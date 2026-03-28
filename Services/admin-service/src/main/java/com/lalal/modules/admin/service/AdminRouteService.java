package com.lalal.modules.admin.service;

import com.lalal.modules.admin.dto.RouteDetailResponse;
import com.lalal.modules.admin.dto.RouteQueryRequest;
import com.lalal.modules.admin.dto.TrainStationSaveRequest;
import com.lalal.modules.dto.PageResult;

import java.util.List;

/**
 * 线路管理服务接口
 */
public interface AdminRouteService {

    /**
     * 分页查询线路列表
     */
    PageResult<RouteDetailResponse> listRoutes(RouteQueryRequest request);

    /**
     * 获取线路详情（含经停站列表）
     */
    RouteDetailResponse getRouteDetail(Long trainId);

    /**
     * 保存列车经停站
     */
    void saveTrainStation(TrainStationSaveRequest request);

    /**
     * 批量保存列车经停站
     */
    void batchSaveTrainStations(Long trainId, List<TrainStationSaveRequest> stations);

    /**
     * 删除列车经停站
     */
    void deleteTrainStation(Long id);

    /**
     * 获取列车的所有经停站
     */
    List<RouteDetailResponse.StationVO> getTrainStations(Long trainId);
}
