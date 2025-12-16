package com.lalal.framework.cache;

import lombok.AllArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
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

    /**
     * 安全获批量取缓存值，支持从数据源回调加载（防止缓存击穿）
     *
     * @param keys          缓存键数组
     * @param loader       数据加载回调（如从 DB 查询）参数本意为匿名指针数组 java用Object[]
     * @param args         对齐缓存键数组 从数据库获取时的必要参数
     * @param cacheTtl     缓存过期时间
     * @param timeUnit     时间单位
     * @param <T>          返回类型
     * @return 缓存值或加载的新值
     */
    public <T> List<T> safeBatchGet(List<String> keys, Function<List<Object[]>, List<T>> loader, List<Object[]> args, long cacheTtl, TimeUnit timeUnit) {
        // 代码通过索引来操作 大大提高性能和减少代码量
        //第一次批量获取
        List<T> cached = new ArrayList<>((List<T>) mutiGet(keys));
        List<Integer> nullKeysIndex=new ArrayList<>();
        for(int i=0;i< cached.size();i++){
            if(cached.get(i)==null){
                nullKeysIndex.add(i);
            }
        }
        if (nullKeysIndex.isEmpty()) {
            return cached;
        }

        // 2. 存在缓存未命中，加分布式锁
        //非常重要 必须排序 保证全线程获取key顺序相同 否则死锁(虽然try不会 破环无限等待了但性能大大降低)
        RLock[] locks = new RLock[nullKeysIndex.size()];
        List<String> nullKeys=nullKeysIndex.stream()
                .map(i->{
                    return "lock"+keys.get(i);
                })
                .sorted()
                .toList();
        for (int idx = 0; idx < nullKeysIndex.size(); idx++) {
            locks[idx] = redissonClient.getLock(nullKeys.get(idx));
        }
        RLock mLock=redissonClient.getMultiLock(locks);
        boolean locked=false;
        try {
            // 尝试获取锁：最多等待 2 秒，持有锁最多 10 秒（防死锁）
            locked = mLock.tryLock(2, 10, TimeUnit.SECONDS);
            if (!locked) {
                //TODO
                throw new RuntimeException("获取分布式锁超时，keys: " +"");
            }

            // 3. 双重检查：可能其他线程已加载
            cached = new ArrayList<>((List<T>) mutiGet(keys));
            nullKeysIndex.clear();
            for(int i=0;i< cached.size();i++){
                if(cached.get(i)==null){
                    nullKeysIndex.add(i);
                }
            }
            if (nullKeysIndex.isEmpty()) {
                return cached;
            }
            List<Object[]> arsFilted=new ArrayList<>(nullKeysIndex.size());
            for(Integer i:nullKeysIndex){
                arsFilted.add(args.get(i));
            }
            // 4. 调用回调加载数据
            List<T> values = loader.apply(arsFilted);

            for(int i=0;i<nullKeysIndex.size();i++){
                cached.set(nullKeysIndex.get(i),values.get(i));
            }
            Map<String,T> map=new HashMap<>();
            for(Integer i:nullKeysIndex){
                map.put(keys.get(i),cached.get(i));
            }
//            redisTemplate.opsForValue().multiSet(map);
            //TODO 批量设置这些键的过期时间


            redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                // 1. 在循环外获取 Serializer，避免重复调用，并处理泛型强转
                RedisSerializer<String> keySerializer = (RedisSerializer<String>) redisTemplate.getKeySerializer();
                RedisSerializer<T> valueSerializer = (RedisSerializer<T>) redisTemplate.getValueSerializer();

// 2. 增加空值校验（Spring Boot 3+ 的 @Nullable 要求）
                if (keySerializer == null || valueSerializer == null) {
                    throw new IllegalArgumentException("Redis serializers cannot be null");
                }
                for (Map.Entry<String, T> entry : map.entrySet()) {
                    // 3. 执行序列化
                    byte[] keyBytes = keySerializer.serialize(entry.getKey());
                    byte[] valueBytes = valueSerializer.serialize(entry.getValue());

                    // 4. 防御性判空（序列化结果可能为空）
                    if (keyBytes == null || valueBytes == null) {
                        continue;
                    }

                    // 5. 调用 setEx (setEx 接收 long 类型时间，不需要强转为 int)
                    connection.setEx(keyBytes, timeUnit.toSeconds(cacheTtl), valueBytes);
                }
                return null;
            });
            return cached;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("获取分布式锁被中断", e);
        } finally {
            if(locked) {
                mLock.unlock();
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