package com.lalal.modules.admin.controller;

import com.lalal.modules.admin.dao.CarriageDO;
import com.lalal.modules.admin.dao.SeatDO;
import com.lalal.modules.admin.dao.TrainDO;
import com.lalal.modules.admin.dto.CarriageSaveRequest;
import com.lalal.modules.admin.dto.TrainQueryRequest;
import com.lalal.modules.admin.service.AdminTrainService;
import com.lalal.modules.dto.PageResult;
import com.lalal.modules.enumType.ReturnCode;
import com.lalal.modules.result.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/train")
public class AdminTrainController {

    @Autowired
    private AdminTrainService adminTrainService;

    /**
     * 分页查询列车列表
     */
    @GetMapping("/list")
    public Result<PageResult<TrainDO>> listTrains(TrainQueryRequest request) {
        return Result.success(adminTrainService.listTrains(request));
    }

    /**
     * 获取列车详情
     */
    @GetMapping("/{id}")
    public Result<TrainDO> getTrain(@PathVariable Long id) {
        // 简单实现，后续可扩展
        return Result.success(null);
    }

    /**
     * 更新列车销售状态
     */
    @PutMapping("/{id}/sale-status")
    public Result<String> updateSaleStatus(@PathVariable Long id, @RequestBody SaleStatusRequest request) {
        try {
            adminTrainService.updateSaleStatus(id, request.getStatus());
            return Result.success("操作成功");
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage(), ReturnCode.fail.code());
        }
    }

    /**
     * 获取列车车厢列表
     */
    @GetMapping("/{trainId}/carriages")
    public Result<List<CarriageDO>> getCarriages(@PathVariable Long trainId) {
        return Result.success(adminTrainService.getCarriages(trainId));
    }

    /**
     * 添加车厢
     */
    @PostMapping("/carriage")
    public Result<String> addCarriage(@RequestBody CarriageSaveRequest request) {
        try {
            adminTrainService.addCarriage(request);
            return Result.success("操作成功");
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage(), ReturnCode.fail.code());
        }
    }

    /**
     * 更新车厢
     */
    @PutMapping("/carriage/{id}")
    public Result<String> updateCarriage(@PathVariable Long id, @RequestBody CarriageSaveRequest request) {
        try {
            adminTrainService.updateCarriage(id, request);
            return Result.success("操作成功");
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage(), ReturnCode.fail.code());
        }
    }

    /**
     * 删除车厢
     */
    @DeleteMapping("/carriage/{id}")
    public Result<String> deleteCarriage(@PathVariable Long id) {
        try {
            adminTrainService.deleteCarriage(id);
            return Result.success("操作成功");
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage(), ReturnCode.fail.code());
        }
    }

    /**
     * 获取车厢座位列表
     */
    @GetMapping("/{trainId}/carriage/{carriageNumber}/seats")
    public Result<List<SeatDO>> getSeats(
            @PathVariable Long trainId,
            @PathVariable String carriageNumber) {
        return Result.success(adminTrainService.getSeats(trainId, carriageNumber));
    }

    /**
     * 更新座位类型
     */
    @PutMapping("/seat/{seatId}/type")
    public Result<String> updateSeatType(@PathVariable Long seatId, @RequestBody SeatTypeRequest request) {
        try {
            adminTrainService.updateSeatType(seatId, request.getSeatType());
            return Result.success("操作成功");
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage(), ReturnCode.fail.code());
        }
    }

    /**
     * 销售状态请求
     */
    @lombok.Data
    public static class SaleStatusRequest {
        private Integer status;
    }

    /**
     * 座位类型请求
     */
    @lombok.Data
    public static class SeatTypeRequest {
        private Integer seatType;
    }
}
