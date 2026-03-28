package com.lalal.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lalal.modules.admin.dao.OperationLogDO;
import com.lalal.modules.admin.dto.LogQueryRequest;
import com.lalal.modules.admin.mapper.OperationLogMapper;
import com.lalal.modules.admin.service.AdminLogService;
import com.lalal.modules.dto.PageResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * 操作日志服务实现
 */
@Service
public class AdminLogServiceImpl implements AdminLogService {

    @Autowired
    private OperationLogMapper operationLogMapper;

    @Override
    public PageResult<OperationLogDO> listLogs(LogQueryRequest request) {
        LambdaQueryWrapper<OperationLogDO> wrapper = new LambdaQueryWrapper<>();

        // 操作人搜索
        if (request.getAdminUsername() != null && !request.getAdminUsername().isEmpty()) {
            wrapper.like(OperationLogDO::getAdminUsername, request.getAdminUsername());
        }

        // 操作类型筛选
        if (request.getOperationType() != null && !request.getOperationType().isEmpty()) {
            wrapper.eq(OperationLogDO::getOperationType, request.getOperationType());
        }

        // 模块筛选
        if (request.getModule() != null && !request.getModule().isEmpty()) {
            wrapper.eq(OperationLogDO::getModule, request.getModule());
        }

        // 状态筛选
        if (request.getStatus() != null) {
            wrapper.eq(OperationLogDO::getStatus, request.getStatus());
        }

        // 时间范围
        if (request.getStartTime() != null) {
            wrapper.ge(OperationLogDO::getCreateTime, request.getStartTime());
        }
        if (request.getEndTime() != null) {
            wrapper.le(OperationLogDO::getCreateTime, request.getEndTime());
        }

        // 按时间降序
        wrapper.orderByDesc(OperationLogDO::getCreateTime);

        Page<OperationLogDO> page = new Page<>(request.getPageNum(), request.getPageSize());
        Page<OperationLogDO> result = operationLogMapper.selectPage(page, wrapper);

        return PageResult.ofPage(result.getRecords(), result.getTotal(), request.getPageNum(), request.getPageSize());
    }

    @Override
    @Async
    public void recordLog(OperationLogDO logDO) {
        try {
            operationLogMapper.insert(logDO);
        } catch (Exception e) {
            // 记录日志失败不影响主业务
            e.printStackTrace();
        }
    }

    @Override
    public OperationLogDO getLogDetail(Long id) {
        return operationLogMapper.selectById(id);
    }

    @Override
    public void cleanExpiredLogs(int days) {
        LambdaQueryWrapper<OperationLogDO> wrapper = new LambdaQueryWrapper<>();
        Date expireDate = new Date(System.currentTimeMillis() - (long) days * 24 * 60 * 60 * 1000);
        wrapper.lt(OperationLogDO::getCreateTime, expireDate);
        operationLogMapper.delete(wrapper);
    }
}
