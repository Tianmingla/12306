package com.lalal.framework.cache;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lalal.framework.cache.RedisSerializer.DefaultValueRedisSerializer;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);


        template.setKeySerializer(new StringRedisSerializer());

        template.setHashKeySerializer(new StringRedisSerializer());

        template.afterPropertiesSet();
        return template;
    }
    @Bean
    public SafeCacheTemplate safeCacheTemplate(RedisTemplate redisTemplate, RedissonClient redissonClient){
        ObjectMapper mapper = new ObjectMapper();
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        CacheContext ctx=CacheContext.builder()
                .mapper(mapper)
                .valueSerializer(new DefaultValueRedisSerializer(mapper))
                .redisType(RedisType.VALUE)
                .build();
        return new SafeCacheTemplate(redisTemplate,redissonClient,ctx);
    }
}
