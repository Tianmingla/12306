package com.lalal.framework.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lalal.framework.cache.RedisSerializer.ValueRedisSerializer;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Component;


@Builder
@Data
public class CacheContext {
    private ValueRedisSerializer valueSerializer;
    private RedisType redisType;
    private ObjectMapper mapper;
}
