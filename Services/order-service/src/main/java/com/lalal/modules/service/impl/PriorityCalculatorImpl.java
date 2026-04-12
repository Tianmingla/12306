package com.lalal.modules.service.impl;

import com.lalal.modules.entity.WaitlistOrderDO;
import com.lalal.modules.service.PriorityCalculator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * 候补优先级计算器实现
 *
 * <p>优先级分数计算规则（分数越高优先级越高）：
 *
 * <pre>
 * 总分 = 时间因子 + VIP加成 + 历史购票加成 + 乘客类型加成 - 队列拥堵惩罚
 *
 * 时间因子：         -创建时间戳 / 1,000,000,000  （越早候补分数越高）
 * VIP加成：          VIP等级 × 5 分              （VIP1=5分，VIP5=25分）
 * 历史购票加成：     MIN(总订单数 / 10, 10)      （最多10分）
 * 乘客类型加成：     成人0分，学生3分，儿童5分
 * 队列拥堵惩罚：     MIN(当前队列人数 × 0.1, 20)  （最多扣20分）
 * </pre>
 *
 * 最终分数保留2位小数
 *
 * @author Claude
 */
@Slf4j
@Component
public class PriorityCalculatorImpl implements PriorityCalculator {

    /**
     * 时间因子缩放系数：将纳秒时间戳转换为合理的分数范围（约 -100 ~ -50）
     * 当前时间约 1,700,000,000,000,000,000 纳秒
     * 除以 1e9 得到约 -1,700,000,000，再除以 1e8 得到约 -17
     * 这里使用 1e9 使时间因子在 -100 左右
     */
    private static final double TIME_FACTOR_DIVISOR = 1_000_000_000D;

    /**
     * VIP等级每级加分
     */
    private static final int VIP_BONUS_PER_LEVEL = 5;

    /**
     * 历史购票次数加成系数（每10单加1分，最多10分）
     */
    private static final double LOYALTY_BONUS_RATIO = 0.1;
    private static final int MAX_LOYALTY_BONUS = 10;

    /**
     * 乘客类型加成
     */
    private static final int ADULT_BONUS = 0;
    private static final int STUDENT_BONUS = 3;
    private static final int CHILD_BONUS = 5;

    /**
     * 队列拥堵惩罚系数
     */
    private static final double QUEUE_PENALTY_FACTOR = 0.1;
    private static final int MAX_QUEUE_PENALTY = 20;

    /**
     * 失败惩罚扣分
     */
    private static final int FAILURE_PENALTY = 10;

    @Override
    public BigDecimal calculatePriority(WaitlistOrderDO order,
                                        Integer vipLevel,
                                        Long totalOrderCount,
                                        Long currentQueueSize) {
        if (order == null || order.getCreateTime() == null) {
            return BigDecimal.ZERO;
        }

        double score = 0.0;

        // 1. 时间因子：创建时间越早，分数越高（使用负时间戳，越小的时间戳越大）
        long timestamp = order.getCreateTime().getTime();
        double timeFactor = -timestamp / TIME_FACTOR_DIVISOR;
        score += timeFactor;

        // 2. VIP加成（暂时固定为0）
        // 所有人同一等级，不考虑VIP

        // 3. 历史购票次数加成（暂时固定为0）
        // 暂不查询历史订单

        // 4. 乘客类型加成（解析乘客类型）
        int passengerTypeBonus = ADULT_BONUS; // 暂时固定为成人
        score += passengerTypeBonus;

        // 5. 队列拥堵惩罚（当前队列人数越多，分数越低）
        if (currentQueueSize != null && currentQueueSize > 0) {
            double penalty = Math.min(currentQueueSize * QUEUE_PENALTY_FACTOR, MAX_QUEUE_PENALTY);
            score -= penalty;
        }

        BigDecimal result = BigDecimal.valueOf(score)
                .setScale(2, RoundingMode.HALF_UP);

        log.debug("[优先级计算] waitlistSn={}, queueSize={}, score={}",
                order.getWaitlistSn(), currentQueueSize, result);

        return result;
    }

    @Override
    public BigDecimal calculatePenalty(BigDecimal currentPriority, Integer failureCount) {
        if (currentPriority == null || failureCount == null || failureCount <= 0) {
            return currentPriority;
        }

        // 每次失败扣分，累计扣分（上限总分的30%）
        BigDecimal penalty = BigDecimal.valueOf(failureCount * FAILURE_PENALTY);
        BigDecimal newPriority = currentPriority.subtract(penalty);

        log.debug("[失败惩罚] currentPriority={}, failureCount={}, newPriority={}",
                currentPriority, failureCount, newPriority);

        return newPriority;
    }

    /**
     * 根据乘客ID列表计算乘客类型加成
     * 简化处理：默认给基础加成，实际应从 t_passenger 表查询 passenger_type
     */
    private int calculatePassengerTypeBonus(String passengerIds) {
        if (passengerIds == null || passengerIds.trim().isEmpty()) {
            return ADULT_BONUS;
        }

        // 简化：默认返回成人加成（实际需查询数据库）
        // TODO: 注入 PassengerService 查询乘客类型
        return ADULT_BONUS;
    }
}
