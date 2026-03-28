package com.lalal.modules.admin.service;

import com.lalal.modules.admin.dao.OperationLogDO;
import com.lalal.modules.admin.dto.LogQueryRequest;
import com.lalal.modules.dto.PageResult;

/**
 * 操作日志服务接口
 */
public interface AdminLogService {

    /**
     * 分页查询操作日志
     */
    PageResult<OperationLogDO> listLogs(LogQueryRequest request);

    /**
     * 记录操作日志
     */
    void recordLog(OperationLogDO logDO);

    /**
     * 获取日志详情
     */
    OperationLogDO getLogDetail(Long id);

    /**
     * 清理过期的操作日志
     */
    void cleanExpiredLogs(int days);
}
