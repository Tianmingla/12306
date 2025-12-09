package com.lalal.framework.cache;

import lombok.AllArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;


@AllArgsConstructor
public class SafeCacheTemplate {
    private RedisTemplate<String, Object> redisTemplate;

    private RedissonClient redissonClient;

    // ============ 基础缓存操作（保持兼容）============

    public void set(String key, Object value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }
    public List<Object> mutiGet(List<String> keys){
        return redisTemplate.opsForValue().multiGet(keys);
    }
    public RedisTemplate instance(){
        return redisTemplate;
    }

    public void del(String key) {
        redisTemplate.delete(key);
    }

    // ============ 安全缓存加载（带回调）============

    /**
     * 安全获取缓存值，支持从数据源回调加载（防止缓存击穿）
     *
     * @param key          缓存键
     * @param loader       数据加载回调（如从 DB 查询）
     * @param cacheTtl     缓存过期时间
     * @param timeUnit     时间单位
     * @param <T>          返回类型
     * @return 缓存值或加载的新值
     */
    public <T> T safeGet(String key, Supplier<T> loader, long cacheTtl, TimeUnit timeUnit) {
        // 1. 先读缓存（无锁，高性能）
        T cached = (T) get(key);
        if (cached != null) {
            return cached;
        }

        // 2. 缓存未命中，加分布式锁
        String lockKey = "lock:" + key;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 尝试获取锁：最多等待 2 秒，持有锁最多 10 秒（防死锁）
            boolean locked = lock.tryLock(2, 10, TimeUnit.SECONDS);
            if (!locked) {
                throw new RuntimeException("获取分布式锁超时，key: " + key);
            }

            // 3. 双重检查：可能其他线程已加载
            cached = (T) get(key);
            if (cached != null) {
                return cached;
            }

            // 4. 调用回调加载数据
            T value = loader.get();

            // 5. 写入缓存（即使为 null，也可考虑缓存空值防穿透）
            if (value != null) {
                set(key, value, cacheTtl, timeUnit);
            } else {
                // 缓存空值，防止缓存穿透（TTL 较短）
                set(key, NULL, 60, TimeUnit.SECONDS);
            }

            return value;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("获取分布式锁被中断", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    // ============ 工具方法 ============

    private static final String NULL="NULL";

    /**
     * 判断是否是空值占位符（用于防穿透）
     */
    public boolean isNullPlaceholder(String obj) {
        return Objects.equals(obj, NULL) || "null".equals(obj);
    }

    // ============ （可选）保留 Lua / Pipeline 方法 ============

    // 注意：如果你后续全面转向 Redisson，这些可能很少用到
    // 但为兼容性暂时保留

//    public Object executeLua(String script, List<Object> keys, List<Object> args) {
//        return redisTemplate.execute((connection) ->
//                connection.eval(
//                        script.getBytes(),
//                        org.springframework.data.redis.connection.ReturnType.VALUE,
//                        keys.size(),
//                        keys.stream().map(k -> k.toString().getBytes()).toArray(byte[][]::new),
//                        args.stream().map(a -> a.toString().getBytes()).toArray(byte[][]::new)
//                )
//        );
//    }

    public List<Object> executePipeline(org.springframework.data.redis.core.RedisCallback<List<Object>> callback) {
        return redisTemplate.executePipelined(callback);
    }
}