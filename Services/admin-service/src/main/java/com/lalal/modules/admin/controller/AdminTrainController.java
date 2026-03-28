package com.lalal.modules.admin.controller;

import com.lalal.modules.admin.dao.TrainDO;
import com.lalal.modules.admin.dto.TrainQueryRequest;
import com.lalal.modules.admin.service.AdminTrainService;
import com.lalal.modules.dto.PageResult;
import com.lalal.modules.enumType.ReturnCode;
import com.lalal.modules.result.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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
     * 销售状态请求
     */
    @lombok.Data
    public static class SaleStatusRequest {
        private Integer status;
    }
}
