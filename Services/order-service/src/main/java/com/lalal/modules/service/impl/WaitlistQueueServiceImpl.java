package com.lalal.modules.service.impl;

import com.lalal.modules.entity.WaitlistOrderDO;
import com.lalal.modules.service.WaitlistQueueService;
import com.lalal.modules.constant.cache.WaitlistCacheConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.Set;

/**
 * 候补队列服务实现（基于 Redis Sorted Set）
 *
 * <p>使用 Redis ZSet 实现优先级队列：
 * - member: waitlistSn
 * - score: 优先级分数（越大越优先）
 * - 出队使用 Lua 脚本原子执行 ZPOPMAX（避免 Redisson 3.21 + Spring Data Redis 3.0 的 popMax 兼容性 bug）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WaitlistQueueServiceImpl implements WaitlistQueueService {

    private final StringRedisTemplate stringRedisTemplate;

    private static final long QUEUE_TTL_DAYS = 10;

    /** Lua 脚本：原子 ZPOPMAX，返回弹出成员的 value（或 nil） */
    private static final DefaultRedisScript<String> ZPOPMAX_SCRIPT = new DefaultRedisScript<>(
            "local result = redis.call('ZPOPMAX', KEYS[1], 1) " +
            "if #result == 0 then return nil end " +
            "return result[1]",
            String.class
    );

    private String buildKey(String trainNumber, String travelDate) {
        return WaitlistCacheConstant.waitlistQueueKey(trainNumber, travelDate, null);
    }

    @Override
    public void enqueue(WaitlistOrderDO order, BigDecimal priority) {
        String key = buildKey(order.getTrainNumber(), order.getTravelDate().toString());
        stringRedisTemplate.opsForZSet().add(key, order.getWaitlistSn(), priority.doubleValue());
        stringRedisTemplate.expire(key, Duration.ofDays(QUEUE_TTL_DAYS));
        log.debug("[候补队列] 入队: waitlistSn={}, priority={}", order.getWaitlistSn(), priority);
    }

    @Override
    public String dequeue(String trainNumber, String travelDate) {
        String key = buildKey(trainNumber, travelDate);
        // Lua 脚本原子 ZPOPMAX，绕过 Redisson Spring Data 集成的 popMax bug
        String waitlistSn = stringRedisTemplate.execute(
                ZPOPMAX_SCRIPT,
                Collections.singletonList(key)
        );
        if (waitlistSn != null) {
            log.info("[候补队列] 出队: waitlistSn={}", waitlistSn);
        }
        return waitlistSn;
    }

    @Override
    public String peek(String trainNumber, String travelDate) {
        String key = buildKey(trainNumber, travelDate);
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
                               BigDecimal newPriority) {
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
        Long rank = stringRedisTemplate.opsForZSet().reverseRank(key, waitlistSn);
        return rank != null ? rank + 1 : null;
    }
}
