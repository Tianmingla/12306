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
import java.util.Set;

/**
 * 候补队列服务实现（基于 Redis Sorted Set）
 *
 * <p>使用 Redis ZSet 实现优先级队列：
 * - member: waitlistSn
 * - score: 优先级分数（越大越优先）
 * - 使用 ZPOPMAX 原子操作取出最高优先级订单
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WaitlistQueueServiceImpl implements WaitlistQueueService {

    private final StringRedisTemplate stringRedisTemplate;

    private static final long QUEUE_TTL_DAYS = 10;

    private String buildKey(String trainNumber, String travelDate) {
        return WaitlistCacheConstant.waitlistQueueKey(trainNumber, travelDate, null);
    }

    @Override
    public void enqueue(WaitlistOrderDO order, java.math.BigDecimal priority) {
        String key = buildKey(order.getTrainNumber(), order.getTravelDate().toString());
        stringRedisTemplate.opsForZSet().add(key, order.getWaitlistSn(), priority.doubleValue());
        stringRedisTemplate.expire(key, Duration.ofDays(QUEUE_TTL_DAYS));
        log.debug("[候补队列] 入队: waitlistSn={}, priority={}", order.getWaitlistSn(), priority);
    }

    @Override
    public String dequeue(String trainNumber, String travelDate) {
        String key = buildKey(trainNumber, travelDate);
        // ZPOPMAX 原子弹出分数最高的成员
        Set<ZSetOperations.TypedTuple<String>> popped = stringRedisTemplate.opsForZSet().popMax(key, 1);
        if (popped == null || popped.isEmpty()) {
            return null;
        }
        String waitlistSn = popped.iterator().next().getValue();
        log.info("[候补队列] 出队: waitlistSn={}", waitlistSn);
        return waitlistSn;
    }

    @Override
    public String peek(String trainNumber, String travelDate) {
        String key = buildKey(trainNumber, travelDate);
        // ZREVRANGE 获取最高分数但不弹出
        Set<String> top = stringRedisTemplate.opsForZSet().reverseRange(key, 0, 0);
        return top != null && !top.isEmpty() ? top.iterator().next() : null;
    }

    @Override
    public Long size(String trainNumber, String travelDate) {
        String key = buildKey(trainNumber, travelDate);
        Long size = stringRedisTemplate.opsForZSet().zCard(key);
        return size != null ? size : 0L;
    }

    @Override
    public void remove(String waitlistSn, String trainNumber, String travelDate) {
        String key = buildKey(trainNumber, travelDate);
        stringRedisTemplate.opsForZSet().remove(key, waitlistSn);
        log.debug("[候补队列] 移除: waitlistSn={}", waitlistSn);
    }

    @Override
    public void updatePriority(String waitlistSn, String trainNumber, String travelDate,
                               java.math.BigDecimal newPriority) {
        String key = buildKey(trainNumber, travelDate);
        stringRedisTemplate.opsForZSet().add(key, waitlistSn, newPriority.doubleValue());
        log.debug("[候补队列] 更新优先级: waitlistSn={}, priority={}", waitlistSn, newPriority);
    }

    @Override
    public Double getScore(String waitlistSn, String trainNumber, String travelDate) {
        String key = buildKey(trainNumber, travelDate);
        return stringRedisTemplate.opsForZSet().score(key, waitlistSn);
    }

    @Override
    public Long getQueuePosition(String waitlistSn, String trainNumber, String travelDate) {
        String key = buildKey(trainNumber, travelDate);
        // ZREVRANK 返回分数从高到低的排名（0-based）
        Long rank = stringRedisTemplate.opsForZSet().reverseRank(key, waitlistSn);
        // 转换为1-based排名
        return rank != null ? rank + 1 : null;
    }
}
