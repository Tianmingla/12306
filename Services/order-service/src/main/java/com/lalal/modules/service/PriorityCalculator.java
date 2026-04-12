package com.lalal.modules.service;

import com.lalal.modules.entity.WaitlistOrderDO;
import java.math.BigDecimal;

/**
 * 候补优先级计算器接口
 */
public interface PriorityCalculator {

    /**
     * 计算候补订单优先级分数
     *
     * @param order 候补订单
     * @param vipLevel 用户VIP等级（0-5，0=普通用户）
     * @param totalOrderCount 用户历史总订单数
     * @param currentQueueSize 当前队列人数（相同车次、日期、座位类型）
     * @return 优先级分数（越大越优先）
     */
    BigDecimal calculatePriority(WaitlistOrderDO order,
                                  Integer vipLevel,
                                  Long totalOrderCount,
                                  Long currentQueueSize);

    /**
     * 计算候补失败惩罚后的优先级
     *
     * @param currentPriority 当前优先级
     * @param failureCount 失败次数
     * @return 惩罚后的优先级
     */
    BigDecimal calculatePenalty(BigDecimal currentPriority, Integer failureCount);
}
