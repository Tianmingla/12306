package com.lalal.modules.service.impl;

import com.lalal.modules.entity.WaitlistLogDO;
import com.lalal.modules.mapper.WaitlistLogMapper;
import com.lalal.modules.service.WaitlistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/**
 * 候补订单操作日志服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WaitlistLogServiceImpl {

    private final WaitlistLogMapper waitlistLogMapper;

    /**
     * 记录操作日志
     */
    @Transactional(rollbackFor = Exception.class)
    public void log(String waitlistSn, String action, Integer statusBefore,
                     Integer statusAfter, String message, String messageId) {
        try {
            WaitlistLogDO log = new WaitlistLogDO();
            log.setWaitlistSn(waitlistSn);
            log.setAction(action);
            log.setStatusBefore(statusBefore);
            log.setStatusAfter(statusAfter);
            log.setMessage(message);
            log.setMessageId(messageId);
            log.setSuccess(1);
            log.setCreateTime(new Date());

            waitlistLogMapper.insert(log);
        } catch (Exception e) {
            log.error("[候补日志] 记录失败: waitlistSn={}, action={}", waitlistSn, action, e);
        }
    }

    /**
     * 记录错误日志
     */
    @Transactional(rollbackFor = Exception.class)
    public void logError(String waitlistSn, String action, String errorMsg) {
        try {
            WaitlistLogDO log = new WaitlistLogDO();
            log.setWaitlistSn(waitlistSn);
            log.setAction(action);
            log.setMessage(errorMsg);
            log.setSuccess(0);
            log.setCreateTime(new Date());

            waitlistLogMapper.insert(log);
        } catch (Exception e) {
            log.error("[候补日志] 记录失败: waitlistSn={}, action={}", waitlistSn, action, e);
        }
    }
}
