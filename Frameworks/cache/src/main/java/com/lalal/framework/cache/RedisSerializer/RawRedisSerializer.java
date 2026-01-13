package com.lalal.framework.cache.RedisSerializer;

import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

//TODO 使用这个序列化器 该类必须继承接口A(提供序列化byte[]方法和从byte[]反序列化)
public class RawRedisSerializer implements ValueRedisSerializer{

    @Override
    public byte[] serialize(Object o) throws SerializationException {
        if(!(o instanceof byte[])){
            throw new SerializationException("序列化对象非原始byte[]类型");
        }
        return new byte[0];
    }

    @Override
    public <T> T deserialize(byte[] bytes, TypeReference<T> typeReference) throws SerializationException {
        return null;
    }
}
