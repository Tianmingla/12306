package com.lalal.modules.admin.controller;

import com.lalal.modules.admin.dao.StationDO;
import com.lalal.modules.admin.dto.StationQueryRequest;
import com.lalal.modules.admin.service.AdminStationService;
import com.lalal.modules.dto.PageResult;
import com.lalal.modules.result.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/station")
public class AdminStationController {

    @Autowired
    private AdminStationService adminStationService;

    /**
     * 分页查询车站列表
     */
    @GetMapping("/list")
    public Result<PageResult<StationDO>> listStations(StationQueryRequest request) {
        return Result.success(adminStationService.listStations(request));
    }

    /**
     * 获取所有车站（下拉选择用）
     */
    @GetMapping("/all")
    public Result<List<StationDO>> listAllStations() {
        return Result.success(adminStationService.listAllStations());
    }

    /**
     * 获取车站详情
     */
    @GetMapping("/{id}")
    public Result<StationDO> getStation(@PathVariable Long id) {
        return Result.success(null);
    }
}
