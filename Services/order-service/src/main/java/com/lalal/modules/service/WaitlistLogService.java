package com.lalal.modules.service;

import com.lalal.modules.entity.WaitlistLogDO;

/**
 * 候补订单操作日志服务接口
 */
public interface WaitlistLogService {

    /**
     * 记录操作日志
     */
    void log(String waitlistSn, String action, Integer statusBefore,
             Integer statusAfter, String message, String messageId);

    /**
     * 记录错误日志
     */
    void logError(String waitlistSn, String action, String errorMsg);
}
