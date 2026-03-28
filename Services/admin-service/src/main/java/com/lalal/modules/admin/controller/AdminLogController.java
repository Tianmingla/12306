package com.lalal.modules.admin.controller;

import com.lalal.modules.admin.dao.OperationLogDO;
import com.lalal.modules.admin.dto.LogQueryRequest;
import com.lalal.modules.admin.service.AdminLogService;
import com.lalal.modules.dto.PageResult;
import com.lalal.modules.result.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 操作日志控制器
 */
@RestController
@RequestMapping("/api/admin/log")
public class AdminLogController {

    @Autowired
    private AdminLogService adminLogService;

    /**
     * 分页查询操作日志
     */
    @GetMapping("/list")
    public Result<PageResult<OperationLogDO>> listLogs(LogQueryRequest request) {
        return Result.success(adminLogService.listLogs(request));
    }

    /**
     * 获取日志详情
     */
    @GetMapping("/{id}")
    public Result<OperationLogDO> getLogDetail(@PathVariable Long id) {
        return Result.success(adminLogService.getLogDetail(id));
    }

    /**
     * 清理过期日志
     */
    @DeleteMapping("/clean")
    public Result<String> cleanExpiredLogs(@RequestParam(defaultValue = "90") int days) {
        adminLogService.cleanExpiredLogs(days);
        return Result.success("清理成功");
    }
}
