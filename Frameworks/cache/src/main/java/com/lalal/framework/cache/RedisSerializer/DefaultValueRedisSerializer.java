package com.lalal.framework.cache.RedisSerializer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class DefaultValueRedisSerializer implements ValueRedisSerializer{
    private final ObjectMapper mapper;
    //为了使配置生效
    public DefaultValueRedisSerializer(ObjectMapper mapper){
        this.mapper=mapper;
    }

    @Override
    public byte[] serialize(Object o) throws SerializationException {
        try{
            String json=mapper.writeValueAsString(o);
            return json.getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new SerializationException(e.getMessage());
        }
    }

    @Override
    public <T> T deserialize(byte[] bytes, TypeReference<T> typeReference) throws SerializationException {
        try {
            return mapper.readValue(bytes,typeReference);
        } catch (IOException e) {
            throw new SerializationException(e.getMessage());
        }
    }
}
