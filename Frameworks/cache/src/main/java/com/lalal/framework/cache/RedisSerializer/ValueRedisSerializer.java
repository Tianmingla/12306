package com.lalal.framework.cache.RedisSerializer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import org.springframework.data.redis.serializer.SerializationException;

public interface ValueRedisSerializer {

    public byte[] serialize(Object o) throws SerializationException ;

    public <T> T deserialize(byte[] bytes, TypeReference<T> typeReference) throws SerializationException;
}
