package com.lalal.modules.admin.controller;

import com.lalal.modules.admin.dto.RouteDetailResponse;
import com.lalal.modules.admin.dto.RouteQueryRequest;
import com.lalal.modules.admin.dto.TrainStationSaveRequest;
import com.lalal.modules.admin.service.AdminRouteService;
import com.lalal.modules.dto.PageResult;
import com.lalal.modules.enumType.ReturnCode;
import com.lalal.modules.result.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 线路管理控制器
 */
@RestController
@RequestMapping("/api/admin/route")
public class AdminRouteController {

    @Autowired
    private AdminRouteService adminRouteService;

    /**
     * 分页查询线路列表
     */
    @GetMapping("/list")
    public Result<PageResult<RouteDetailResponse>> listRoutes(RouteQueryRequest request) {
        return Result.success(adminRouteService.listRoutes(request));
    }

    /**
     * 获取线路详情（含经停站列表）
     */
    @GetMapping("/{trainId}")
    public Result<RouteDetailResponse> getRouteDetail(@PathVariable Long trainId) {
        return Result.success(adminRouteService.getRouteDetail(trainId));
    }

    /**
     * 获取列车的所有经停站
     */
    @GetMapping("/{trainId}/stations")
    public Result<List<RouteDetailResponse.StationVO>> getTrainStations(@PathVariable Long trainId) {
        return Result.success(adminRouteService.getTrainStations(trainId));
    }

    /**
     * 新增经停站
     */
    @PostMapping("/station")
    public Result<String> addTrainStation(@RequestBody TrainStationSaveRequest request) {
        try {
            adminRouteService.saveTrainStation(request);
            return Result.success("操作成功");
        } catch (Exception e) {
            return Result.fail(e.getMessage(), ReturnCode.fail.code());
        }
    }

    /**
     * 批量保存列车经停站
     */
    @PostMapping("/{trainId}/stations")
    public Result<String> batchSaveTrainStations(
            @PathVariable Long trainId,
            @RequestBody List<TrainStationSaveRequest> stations) {
        try {
            adminRouteService.batchSaveTrainStations(trainId, stations);
            return Result.success("操作成功");
        } catch (Exception e) {
            return Result.fail(e.getMessage(), ReturnCode.fail.code());
        }
    }

    /**
     * 删除经停站
     */
    @DeleteMapping("/station/{id}")
    public Result<String> deleteTrainStation(@PathVariable Long id) {
        try {
            adminRouteService.deleteTrainStation(id);
            return Result.success("操作成功");
        } catch (Exception e) {
            return Result.fail(e.getMessage(), ReturnCode.fail.code());
        }
    }
}
