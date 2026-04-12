package com.lalal.modules.service.impl;

import com.lalal.modules.entity.WaitlistOrderDO;
import com.lalal.modules.service.WaitlistQueueService;
import com.lalal.modules.constant.cache.WaitlistCacheConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 候补队列服务实现（基于 Redis Sorted Set）
 *
 * <p>使用 Redis ZSet 实现优先级队列：
 * - member: waitlistSn
 * - score: 优先级分数（越大越优先）
 * - 支持按车次/日期/座位类型分队列
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WaitlistQueueServiceImpl implements WaitlistQueueService {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 队列过期时间（天）
     */
    private static final long QUEUE_TTL_DAYS = 10;

    /**
     * 构建队列 Key
     */
    private String buildKey(String trainNumber, String travelDate, Integer seatType) {
        return WaitlistCacheConstant.waitlistQueueKey(trainNumber, travelDate, seatType);
    }

    @Override
    public void enqueue(WaitlistOrderDO order, java.math.BigDecimal priority) {
        String key = buildKey(
                order.getTrainNumber(),
                order.getTravelDate().toString(),
                null  // 暂按统一队列（可扩展为按座位类型分队列）
        );

        double score = priority.doubleValue();

        stringRedisTemplate.opsForZSet().add(key, order.getWaitlistSn(), score);
        stringRedisTemplate.expire(key, Duration.ofDays(QUEUE_TTL_DAYS));

        log.debug("[候补队列] 入队成功: waitlistSn={}, priority={}",
                  order.getWaitlistSn(), score);
    }

    @Override
    public String dequeue(String trainNumber, String travelDate, Integer seatType) {
        String key = buildKey(trainNumber, travelDate, seatType);

        // 弹出分数最高的成员（score 最大）
        Set<ZSetOperations.TypedTuple<String>> popped =
                stringRedisTemplate.opsForZSet().popMax(key, 1);

        if (popped == null || popped.isEmpty()) {
            return null;
        }

        String waitlistSn = popped.iterator().next().getValue();
        log.debug("[候补队列] 出队: waitlistSn={}", waitlistSn);

        return waitlistSn;
    }

    @Override
    public Long getQueuePosition(String waitlistSn, String trainNumber, String travelDate) {
        String key = buildKey(trainNumber, travelDate, null);

        // reverseRank: 分数从高到低排序，rank 从0开始
        Long rank = stringRedisTemplate.opsForZSet().reverseRank(key, waitlistSn);
        return rank != null ? rank + 1 : null;
    }

    @Override
    public void remove(String waitlistSn, String trainNumber, String travelDate) {
        String key = buildKey(trainNumber, travelDate, null);
        Long removed = stringRedisTemplate.opsForZSet().remove(key, waitlistSn);

        if (removed != null && removed > 0) {
            log.debug("[候补队列] 移除成功: waitlistSn={}", waitlistSn);
        }
    }

    @Override
    public void updatePriority(String waitlistSn, String trainNumber, String travelDate,
                               java.math.BigDecimal newPriority) {
        String key = buildKey(trainNumber, travelDate, null);
        Double score = newPriority.doubleValue();

        // ZSet 的 add 会更新已存在成员的 score
        Boolean added = stringRedisTemplate.opsForZSet().add(key, waitlistSn, score);
        if (Boolean.TRUE.equals(added)) {
            log.debug("[候补队列] 更新优先级: waitlistSn={}, newPriority={}",
                      waitlistSn, score);
        }
    }

    @Override
    public Long getQueueSize(String trainNumber, String travelDate, Integer seatType) {
        String key = buildKey(trainNumber, travelDate, seatType);
        Long size = stringRedisTemplate.opsForZSet().zCard(key);
        return size != null ? size : 0L;
    }

    /**
     * 批量获取队列中所有候补订单号（按分数降序）
     *
     * @param trainNumber 车次号
     * @param travelDate 乘车日期
     * @param seatType 座位类型
     * @param offset 起始位置（从0开始）
     * @param limit 返回数量
     * @return 候补订单号列表
     */
    public List<String> getQueueList(String trainNumber, String travelDate,
                                      Integer seatType, int offset, int limit) {
        String key = buildKey(trainNumber, travelDate, seatType);

        Set<String> result = stringRedisTemplate.opsForZSet()
                .reverseRange(key, offset, offset + limit - 1);

        return result != null ? result.stream().toList() : Collections.emptyList();
    }

    /**
     * 批量获取队列中所有候补订单号及分数
     *
     * @param trainNumber 车次号
     * @param travelDate 乘车日期
     * @param seatType 座位类型
     * @return waitlistSn -> score 映射
     */
    public java.util.Map<String, Double> getQueueWithScores(String trainNumber, String travelDate,
                                                             Integer seatType) {
        String key = buildKey(trainNumber, travelDate, seatType);

        Set<ZSetOperations.TypedTuple<String>> tuples =
                stringRedisTemplate.opsForZSet().rangeWithScores(key, 0, -1);

        if (tuples == null) {
            return Collections.emptyMap();
        }

        return tuples.stream()
                .collect(Collectors.toMap(
                        ZSetOperations.TypedTuple::getValue,
                        tuple -> tuple.getScore() != null ? tuple.getScore() : 0.0
                ));
    }
}
