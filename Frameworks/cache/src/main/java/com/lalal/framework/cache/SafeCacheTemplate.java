package com.lalal.framework.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;


@AllArgsConstructor
public class SafeCacheTemplate {
    private RedisTemplate<String, Object> redisTemplate;

    private RedissonClient redissonClient;

//    private RedisSerializer<Object> curValueSerializer;

//    private Map<RedisType,Function<List<String>,List<Object>>> getter;
//    private Map<RedisType,RedisType> setter;
//
//    SafeCacheTemplate(RedisTemplate redisTemplate,RedissonClient redissonClient){
//        this.redisTemplate=redisTemplate;
//        this.redissonClient=redissonClient;
//
//
//
//    }

    // ============ 基础缓存操作（保持兼容）============

    public void set(String key, Object value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }
    public <T> T get(String key, TypeReference<T> typeReference) {
        return redisTemplate.execute((RedisCallback<T>) connection -> {
            byte[] keyBytes =((RedisSerializer<String>)redisTemplate.getKeySerializer()).serialize(key);
            byte[] valueBytes = connection.get(keyBytes);
            if(valueBytes==null){
                return null;
            }
            T result=null;
            ObjectMapper mapper=new ObjectMapper();
            mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
            try {
                result=mapper.readValue(valueBytes,typeReference);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return result;
        });
    }
    public List<Object> multiGet(List<String> keys){
        return redisTemplate.opsForValue().multiGet(keys);
    }
    public <T> List<T> multiGet(List<String> keys, TypeReference<T> typeRef) {
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }

        // 使用 execute + 手动 pipeline
        List<Object> rawResults = redisTemplate.execute((RedisCallback<List<Object>>)  connection -> {
            // 1. 开启 pipeline
            connection.openPipeline();

            // 2. 获取 key 序列化器
            RedisSerializer<String> keySerializer = (RedisSerializer<String>) redisTemplate.getKeySerializer();

            // 3. 发送所有 GET 命令
            for (String key : keys) {
                byte[] keyBytes = keySerializer.serialize(key);
                connection.get(keyBytes); // 注意：这里只是 enqueue，不立即执行
            }

            // 4. 关闭 pipeline 并获取原始响应（List<byte[]> 或 null）
            List<Object> responses = connection.closePipeline();

            // 5. 返回原始字节结果（Spring 不会再做任何反序列化！）
            return responses;
        });

        // 6. 手动反序列化（此时 rawResults 中每个非 null 元素是 byte[]）
        ObjectMapper mapper = new ObjectMapper();
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        List<T> results = new ArrayList<>(rawResults.size());
        for (Object obj : rawResults) {
            if (obj == null) {
                results.add(null);
            } else {
                try {
                    T value = mapper.readValue((byte[]) obj, typeRef);
                    results.add(value);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to deserialize Redis value", e);
                }
            }
        }

        return results;
    }
    public List<Map<String,Object>> mutiHGet(List<String> keys){
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }

        // 使用 pipeline 批量执行 HGETALL
        List<Object> results = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            RedisSerializer<String> keySerializer = redisTemplate.getStringSerializer();
            for (String key : keys) {
                connection.hashCommands().hGetAll(keySerializer.serialize(key));
            }
            return null;
        });

        // 转换结果：每个 result 是一个 Map<byte[], byte[]>，需反序列化
        return results.stream().map(item -> {
            if (item == null || !(item instanceof Map)) {
                return null;
            }
            @SuppressWarnings("unchecked")
            Map<byte[], byte[]> rawMap = (Map<byte[], byte[]>) item;
            Map<String, Object> deserialized = new HashMap<>();
            for (Map.Entry<byte[], byte[]> entry : rawMap.entrySet()) {
                String field = (String) redisTemplate.getKeySerializer().deserialize(entry.getKey());
                Object value = redisTemplate.getValueSerializer().deserialize(entry.getValue());
                deserialized.put(field, value);
            }
            return deserialized;
        }).collect(Collectors.toList());
    }
    public <T> List<List<T>> multiLGet(List<String> keys){
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }
        // 使用 pipeline 批量执行 HGETALL
        List<Object> results = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            RedisSerializer<String> keySerializer = redisTemplate.getStringSerializer();
            for (String key : keys) {
                byte[] keyBytes=keySerializer.serialize(key);
                connection.listCommands().lRange(keyBytes,0,-1);
            }
            return null;
        });
        return results
                .stream()
                .map(r->{
                    List<T> result=(List<T>) r;
                    if(r==null||result.isEmpty()){
                        return null;
                    }
                    return result;
                })
                .toList();
    }
    public <T> List<List<T>> multiLGet(List<String> keys, TypeReference<T> typeReference) {
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }

        List<Object> raw = redisTemplate.execute((RedisCallback<List<Object>>)  connection -> {
            connection.openPipeline();

            RedisSerializer<String> keySerializer = redisTemplate.getStringSerializer();
            for (String key : keys) {
                byte[] keyBytes = keySerializer.serialize(key);
                connection.lRange(keyBytes, 0, -1);
            }

            return connection.closePipeline();
        });

        ObjectMapper mapper = new ObjectMapper();
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        List<List<T>> results = new ArrayList<>(raw.size());

        for (Object obj : raw) {
            @SuppressWarnings("unchecked")
            List<byte[]> byteList = (List<byte[]>) obj;
            if (byteList.isEmpty()) {
                results.add(null);
            } else {
                List<T> innerList = new ArrayList<>(byteList.size());
                for (byte[] bytes : byteList) {
                    if (bytes == null) {
                        innerList.add(null);
                    } else {
                        try {
                            T value = mapper.readValue(bytes, typeReference);
                            innerList.add(value);
                        } catch (IOException e) {
                            throw new RuntimeException("Deserialize failed", e);
                        }
                    }
                }
                results.add(innerList);
            }
        }

        return results;
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
    @Deprecated
    public <T> T safeGet(String key, Supplier<T> loader, long cacheTtl, TimeUnit timeUnit) {
        // 1. 先读缓存（无锁，高性能）
        T cached = (T)get(key);
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
     * 安全获取缓存值，支持从数据源回调加载（防止缓存击穿）
     *
     * @param key          缓存键
     * @param loader       数据加载回调（如从 DB 查询）
     * @param typeReference  类型信息 针对反序列化关闭@class
     * @param cacheTtl     缓存过期时间
     * @param timeUnit     时间单位
     * @param <T>          返回类型
     * @return 缓存值或加载的新值
     */
    public <T> T safeGet(String key, Supplier<T> loader,TypeReference<T> typeReference, long cacheTtl, TimeUnit timeUnit) {
        // 1. 先读缓存（无锁，高性能）
        T cached = get(key,typeReference);
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
            cached = get(key,typeReference);
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
    @Deprecated
    public <T> List<T> safeBatchGet(List<String> keys, Function<List<Object[]>, List<T>> loader, List<Object[]> args, long cacheTtl, TimeUnit timeUnit) {
        // 代码通过索引来操作 大大提高性能和减少代码量
        //第一次批量获取
        List<T> cached = new ArrayList<>((List<T>) multiGet(keys));
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

        List<String> nullKeys=nullKeysIndex.stream()
                .map(i->{
                    return "lock"+keys.get(i);
                })
                .sorted()
                .distinct() //去重 否则死锁！
                .toList();
        RLock[] locks = new RLock[nullKeys.size()];
        for (int idx = 0; idx < nullKeys.size(); idx++) {
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
            cached = new ArrayList<>((List<T>) multiGet(keys));
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

            redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                RedisSerializer<String> keySerializer = (RedisSerializer<String>) redisTemplate.getKeySerializer();
                RedisSerializer<T> valueSerializer = (RedisSerializer<T>) redisTemplate.getValueSerializer();

                if (keySerializer == null || valueSerializer == null) {
                    throw new IllegalArgumentException("Redis serializers cannot be null");
                }
                for (Map.Entry<String, T> entry : map.entrySet()) {
                    byte[] keyBytes = keySerializer.serialize(entry.getKey());
                    byte[] valueBytes = valueSerializer.serialize(entry.getValue());
                    if (keyBytes == null || valueBytes == null) {
                        continue;
                    }
                    // 调用 setEx (setEx 接收 long 类型时间，不需要强转为 int)
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
    public <T> List<T> safeBatchGet(List<String> keys, Function<List<Object[]>, List<T>> loader, TypeReference<T> typeReference,List<Object[]> args, long cacheTtl, TimeUnit timeUnit) {
        // 代码通过索引来操作 大大提高性能和减少代码量
        //第一次批量获取
        List<T> cached = this.multiGet(keys,typeReference);
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

        List<String> nullKeys=nullKeysIndex.stream()
                .map(i->{
                    return "lock"+keys.get(i);
                })
                .sorted()
                .distinct() //去重 否则死锁！
                .toList();
        RLock[] locks = new RLock[nullKeys.size()];
        for (int idx = 0; idx < nullKeys.size(); idx++) {
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
            cached = this.multiGet(keys,typeReference);
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

            redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                RedisSerializer<String> keySerializer = (RedisSerializer<String>) redisTemplate.getKeySerializer();
                RedisSerializer<T> valueSerializer = (RedisSerializer<T>) redisTemplate.getValueSerializer();

                if (keySerializer == null || valueSerializer == null) {
                    throw new IllegalArgumentException("Redis serializers cannot be null");
                }
                for (Map.Entry<String, T> entry : map.entrySet()) {
                    byte[] keyBytes = keySerializer.serialize(entry.getKey());
                    byte[] valueBytes = valueSerializer.serialize(entry.getValue());
                    if (keyBytes == null || valueBytes == null) {
                        continue;
                    }
                    // 调用 setEx (setEx 接收 long 类型时间，不需要强转为 int)
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
    /**
     * 安全获批量取Hash缓存值，支持从数据源回调加载（防止缓存击穿）
     *
     * @param keys          缓存键数组
     * @param loader       数据加载回调（如从 DB 查询）参数本意为匿名指针数组 java用Object[]
     * @param args         对齐缓存键数组 从数据库获取时的必要参数
     * @param cacheTtl     缓存过期时间
     * @param timeUnit     时间单位
     * @return 缓存值或加载的新值
     */
    public List<Map<String,Object>> safeBatchHGet(List<String> keys, Function<List<Object[]>, List<Map<String,Object>>> loader, List<Object[]> args, long cacheTtl, TimeUnit timeUnit) {
        // 代码通过索引来操作 大大提高性能和减少代码量
        //第一次批量获取
        List<Map<String,Object>> cached = new ArrayList<>(mutiHGet(keys));
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
        List<String> nullKeys=nullKeysIndex.stream()
                .map(i-> "lock"+keys.get(i))
                .sorted()
                .distinct() //去重 否则死锁！
                .toList();
        RLock[] locks = new RLock[nullKeys.size()];
        for (int idx = 0; idx < nullKeys.size(); idx++) {
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
            cached = new ArrayList<>(mutiHGet(keys));
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
            List<Map<String,Object>> values = loader.apply(arsFilted);

            for(int i=0;i<nullKeysIndex.size();i++){
                cached.set(nullKeysIndex.get(i),values.get(i));
            }
            Map<String,Map<String,Object>> map=new HashMap<>();
            for(Integer i:nullKeysIndex){
                map.put(keys.get(i),cached.get(i));
            }

            redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                RedisSerializer<String> keySerializer = (RedisSerializer<String>) redisTemplate.getKeySerializer();
                RedisSerializer<Object> valueSerializer = (RedisSerializer<Object>) redisTemplate.getHashValueSerializer();
                RedisSerializer<String> filedSerializer=(RedisSerializer<String>) redisTemplate.getHashKeySerializer();

                if (keySerializer == null || valueSerializer == null) {
                    throw new IllegalArgumentException("Redis serializers cannot be null");
                }
                for (Map.Entry<String, Map<String,Object>> entry : map.entrySet()) {
                    byte[] keyBytes = keySerializer.serialize(entry.getKey());
                    Map<byte[],byte[]> hashes=new HashMap<>();
                    if (keyBytes == null) {
                        continue;
                    }
                    for(Map.Entry<String,Object> entryMap:entry.getValue().entrySet()){
                        byte[] filed=filedSerializer.serialize(entryMap.getKey());
                        byte[] value= valueSerializer.serialize(entryMap.getValue());
                        hashes.put(filed,value);
                    }
                    connection.hashCommands().hMSet(keyBytes,hashes);
                    connection.keyCommands().expire(keyBytes,timeUnit.toSeconds(cacheTtl));
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
    /**
     * 安全获批量取List缓存值，支持从数据源回调加载（防止缓存击穿）
     *
     * @param keys          缓存键数组
     * @param loader       数据加载回调（如从 DB 查询）参数本意为匿名指针数组 java用Object[]
     * @param args         对齐缓存键数组 从数据库获取时的必要参数
     * @param cacheTtl     缓存过期时间
     * @param timeUnit     时间单位
     * @return 缓存值或加载的新值
     */
    @Deprecated
    public <T> List<List<T>> safeBatchLGet(List<String> keys, Function<List<Object[]>, List<List<T>>> loader, List<Object[]> args, long cacheTtl, TimeUnit timeUnit) {
        // 代码通过索引来操作 大大提高性能和减少代码量
        //第一次批量获取
        List<List<T>> cached = new ArrayList<>(multiLGet(keys));
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
        List<String> nullKeys=nullKeysIndex.stream()
                .map(i-> "lock"+keys.get(i))
                .sorted()
                .distinct() //去重 否则死锁！
                .toList();
        RLock[] locks = new RLock[nullKeys.size()];
        for (int idx = 0; idx < nullKeys.size(); idx++) {
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
            cached = new ArrayList<>(multiLGet(keys));
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
            List<List<T>> values = loader.apply(arsFilted);

            for(int i=0;i<nullKeysIndex.size();i++){
                cached.set(nullKeysIndex.get(i),values.get(i));
            }
            Map<String,List<T>> map=new HashMap<>();
            for(Integer i:nullKeysIndex){
                map.put(keys.get(i),cached.get(i));
            }

            redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                RedisSerializer<String> keySerializer = (RedisSerializer<String>) redisTemplate.getKeySerializer();
                RedisSerializer<T> valueSerializer = (RedisSerializer<T>) redisTemplate.getValueSerializer();
                if (keySerializer == null || valueSerializer == null) {
                    throw new IllegalArgumentException("Redis serializers cannot be null");
                }
                for(Map.Entry<String,List<T>> entry: map.entrySet()){
                    byte[] keyBytes=keySerializer.serialize(entry.getKey());
                    int size=entry.getValue().size();
                    byte[][] valueList=new byte[size][];
                    for(int i=0;i<size;i++){
                        valueList[i]= valueSerializer.serialize(entry.getValue().get(i));
                    }
                    connection.listCommands().rPush(keyBytes,valueList);
                    connection.keyCommands().expire(keyBytes, timeUnit.toSeconds(cacheTtl));
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
    /**
     * 安全获批量取List缓存值，支持从数据源回调加载（防止缓存击穿）
     *
     * @param keys          缓存键数组
     * @param loader       数据加载回调（如从 DB 查询）参数本意为匿名指针数组 java用Object[]
     * @param typeReference  类型信息
     * @param args         对齐缓存键数组 从数据库获取时的必要参数
     * @param cacheTtl     缓存过期时间
     * @param timeUnit     时间单位
     * @return 缓存值或加载的新值
     */
    public <T> List<List<T>> safeBatchLGet(List<String> keys, Function<List<Object[]>, List<List<T>>> loader,TypeReference<T> typeReference ,List<Object[]> args, long cacheTtl, TimeUnit timeUnit) {
        // 代码通过索引来操作 大大提高性能和减少代码量
        //第一次批量获取
        List<List<T>> cached = multiLGet(keys,typeReference);
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
        List<String> nullKeys=nullKeysIndex.stream()
                .map(i-> "lock"+keys.get(i))
                .sorted()
                .distinct() //去重 否则死锁！
                .toList();
        RLock[] locks = new RLock[nullKeys.size()];
        for (int idx = 0; idx < nullKeys.size(); idx++) {
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
            cached = multiLGet(keys,typeReference);
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
            List<List<T>> values = loader.apply(arsFilted);

            for(int i=0;i<nullKeysIndex.size();i++){
                cached.set(nullKeysIndex.get(i),values.get(i));
            }
            Map<String,List<T>> map=new HashMap<>();
            for(Integer i:nullKeysIndex){
                map.put(keys.get(i),cached.get(i));
            }

            redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                RedisSerializer<String> keySerializer = (RedisSerializer<String>) redisTemplate.getKeySerializer();
                RedisSerializer<T> valueSerializer = (RedisSerializer<T>) redisTemplate.getValueSerializer();
                if (keySerializer == null || valueSerializer == null) {
                    throw new IllegalArgumentException("Redis serializers cannot be null");
                }
                for(Map.Entry<String,List<T>> entry: map.entrySet()){
                    byte[] keyBytes=keySerializer.serialize(entry.getKey());
                    int size=entry.getValue().size();
                    byte[][] valueList=new byte[size][];
                    for(int i=0;i<size;i++){
                        valueList[i]= valueSerializer.serialize(entry.getValue().get(i));
                    }
                    connection.listCommands().rPush(keyBytes,valueList);
                    connection.keyCommands().expire(keyBytes, timeUnit.toSeconds(cacheTtl));
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

//
//    public Object executeLua(String script, List<Object> keys, List<Object> args,ReturnType returnType) {
//        return redisTemplate.executePipelined((RedisCallback<?>) (connection) ->
//                connection.eval(
//                        script.getBytes(),
//                        returnType,
//                        keys.size(),
//                        keys.stream().map(k -> k.toString().getBytes()).toArray(byte[][]::new)
//                        args.stream().map(a -> a.toString().getBytes()).toArray(byte[][]::new)
//                )
//        );
//    }

    public List<Object> executePipeline(org.springframework.data.redis.core.RedisCallback<List<Object>> callback) {
        return redisTemplate.executePipelined(callback);
    }
}