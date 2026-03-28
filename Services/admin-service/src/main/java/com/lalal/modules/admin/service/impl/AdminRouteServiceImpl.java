package com.lalal.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lalal.modules.admin.dao.TrainDO;
import com.lalal.modules.admin.dao.TrainStationDO;
import com.lalal.modules.admin.dto.RouteDetailResponse;
import com.lalal.modules.admin.dto.RouteQueryRequest;
import com.lalal.modules.admin.dto.TrainStationSaveRequest;
import com.lalal.modules.admin.mapper.TrainMapper;
import com.lalal.modules.admin.mapper.TrainStationMapper;
import com.lalal.modules.admin.service.AdminRouteService;
import com.lalal.modules.dto.PageResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 线路管理服务实现
 */
@Service
public class AdminRouteServiceImpl implements AdminRouteService {

    @Autowired
    private TrainMapper trainMapper;

    @Autowired
    private TrainStationMapper trainStationMapper;

    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm");

    @Override
    public PageResult<RouteDetailResponse> listRoutes(RouteQueryRequest request) {
        // 1. 查询列车列表
        LambdaQueryWrapper<TrainDO> trainWrapper = new LambdaQueryWrapper<>();
        trainWrapper.eq(TrainDO::getDelFlag, 0);

        // 车次号搜索
        if (request.getTrainNumber() != null && !request.getTrainNumber().isEmpty()) {
            trainWrapper.like(TrainDO::getTrainNumber, request.getTrainNumber());
        }

        // 按ID降序
        trainWrapper.orderByDesc(TrainDO::getId);

        Page<TrainDO> page = new Page<>(request.getPageNum(), request.getPageSize());
        Page<TrainDO> trainResult = trainMapper.selectPage(page, trainWrapper);

        if (trainResult.getRecords().isEmpty()) {
            return PageResult.ofPage(new ArrayList<>(), 0L, request.getPageNum(), request.getPageSize());
        }

        // 2. 获取所有列车ID
        List<Long> trainIds = trainResult.getRecords().stream()
                .map(TrainDO::getId)
                .collect(Collectors.toList());

        // 3. 查询每列车的经停站信息（首站和末站）
        LambdaQueryWrapper<TrainStationDO> stationWrapper = new LambdaQueryWrapper<>();
        stationWrapper.in(TrainStationDO::getTrainId, trainIds);
        stationWrapper.orderByAsc(TrainStationDO::getSequence);
        List<TrainStationDO> allStations = trainStationMapper.selectList(stationWrapper);

        // 4. 按列车ID分组
        Map<Long, List<TrainStationDO>> stationMap = allStations.stream()
                .collect(Collectors.groupingBy(TrainStationDO::getTrainId));

        // 5. 组装响应数据
        List<RouteDetailResponse> routes = trainResult.getRecords().stream()
                .map(train -> buildRouteResponse(train, stationMap.get(train.getId()), request.getStationName()))
                .filter(r -> r != null) // 过滤掉不符合条件的
                .collect(Collectors.toList());

        return PageResult.ofPage(routes, trainResult.getTotal(), request.getPageNum(), request.getPageSize());
    }

    /**
     * 构建线路响应对象
     */
    private RouteDetailResponse buildRouteResponse(TrainDO train, List<TrainStationDO> stations, String stationNameFilter) {
        RouteDetailResponse response = new RouteDetailResponse();
        response.setTrainId(train.getId());
        response.setTrainNumber(train.getTrainNumber());

        if (stations == null || stations.isEmpty()) {
            response.setStationCount(0);
            return response;
        }

        // 设置站点数量
        response.setStationCount(stations.size());

        // 设置起点站和终点站
        TrainStationDO firstStation = stations.get(0);
        TrainStationDO lastStation = stations.get(stations.size() - 1);

        response.setStartStation(firstStation.getStationName());
        response.setEndStation(lastStation.getStationName());

        // 设置出发时间（首站出站时间）
        if (firstStation.getDepartureTime() != null) {
            response.setDepartureTime(TIME_FORMAT.format(firstStation.getDepartureTime()));
        }

        // 设置到达时间（末站到站时间）
        if (lastStation.getArrivalTime() != null) {
            response.setArrivalTime(TIME_FORMAT.format(lastStation.getArrivalTime()));
        }

        // 计算运行时长（分钟）
        if (firstStation.getDepartureTime() != null && lastStation.getArrivalTime() != null) {
            long duration = (lastStation.getArrivalTime().getTime() - firstStation.getDepartureTime().getTime()) / (1000 * 60);
            response.setDuration((int) duration);
        }

        // 如果有车站名称过滤，检查是否包含该站
        if (stationNameFilter != null && !stationNameFilter.isEmpty()) {
            boolean contains = stations.stream()
                    .anyMatch(s -> s.getStationName() != null && s.getStationName().contains(stationNameFilter));
            if (!contains) {
                return null; // 不包含该站，过滤掉
            }
        }

        // 设置站点列表
        List<RouteDetailResponse.StationVO> stationVOList = stations.stream()
                .map(this::convertToStationVO)
                .collect(Collectors.toList());
        response.setStations(stationVOList);

        return response;
    }

    /**
     * 转换为站点VO
     */
    private RouteDetailResponse.StationVO convertToStationVO(TrainStationDO station) {
        RouteDetailResponse.StationVO vo = new RouteDetailResponse.StationVO();
        vo.setId(station.getId());
        vo.setSequence(station.getSequence());
        vo.setStationName(station.getStationName());
        vo.setStopoverTime(station.getStopoverTime());

        if (station.getArrivalTime() != null) {
            vo.setArrivalTime(TIME_FORMAT.format(station.getArrivalTime()));
        }
        if (station.getDepartureTime() != null) {
            vo.setDepartureTime(TIME_FORMAT.format(station.getDepartureTime()));
        }

        return vo;
    }

    @Override
    public RouteDetailResponse getRouteDetail(Long trainId) {
        TrainDO train = trainMapper.selectById(trainId);
        if (train == null) {
            throw new RuntimeException("列车不存在");
        }

        // 查询经停站
        LambdaQueryWrapper<TrainStationDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TrainStationDO::getTrainId, trainId)
                .orderByAsc(TrainStationDO::getSequence);
        List<TrainStationDO> stations = trainStationMapper.selectList(wrapper);

        return buildRouteResponse(train, stations, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveTrainStation(TrainStationSaveRequest request) {
        TrainStationDO station = new TrainStationDO();
        station.setTrainId(request.getTrainId());
        station.setTrainNumber(request.getTrainNumber());
        station.setStationId(request.getStationId());
        station.setStationName(request.getStationName());
        station.setSequence(request.getSequence());
        station.setArrivalTime(request.getArrivalTime());
        station.setDepartureTime(request.getDepartureTime());
        station.setStopoverTime(request.getStopoverTime());
        station.setRunDate(request.getRunDate());

        trainStationMapper.insert(station);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchSaveTrainStations(Long trainId, List<TrainStationSaveRequest> stations) {
        // 先删除该列车的所有经停站
        LambdaQueryWrapper<TrainStationDO> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(TrainStationDO::getTrainId, trainId);
        trainStationMapper.delete(deleteWrapper);

        // 批量插入新的经停站
        if (stations != null && !stations.isEmpty()) {
            for (TrainStationSaveRequest request : stations) {
                request.setTrainId(trainId);
                saveTrainStation(request);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTrainStation(Long id) {
        trainStationMapper.deleteById(id);
    }

    @Override
    public List<RouteDetailResponse.StationVO> getTrainStations(Long trainId) {
        LambdaQueryWrapper<TrainStationDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TrainStationDO::getTrainId, trainId)
                .orderByAsc(TrainStationDO::getSequence);
        List<TrainStationDO> stations = trainStationMapper.selectList(wrapper);

        return stations.stream()
                .map(this::convertToStationVO)
                .collect(Collectors.toList());
    }
}
