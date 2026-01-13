package com.lalal.modules.model.Serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.lalal.framework.cache.RedisSerializer.ValueRedisSerializer;
import com.lalal.modules.model.BooleanMask;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;

//自定义序列化器
@Component
public class BooleanMaskSerializer implements ValueRedisSerializer {


    @Override
    public byte[] serialize(Object o) throws SerializationException {
        BooleanMask mask=(BooleanMask) o;
//        StringBuilder sb = new StringBuilder();
//        for (int i = 0; i < mask.getSize(); i++) {
//            sb.append(mask.get(i) ? '1' : '0');
//        }
//
        int numBits= mask.getSize();
        int numBytes = (numBits + 7) / 8; // 向上取整
        byte[] bytes = new byte[numBytes+1];

        for (int i = 0; i < numBits; i++) {
            if (mask.get(i)) {
                bytes[i / 8] |= (1 << (i % 8));//位图使用小端序 而不是直觉上的大端序 byte[]字节数组就是大端序 但计算机大多是高地址-》低地址
            }
        }
        bytes[numBytes]=(byte) numBits; //最后一字节放大小 不影响lua脚本位运算操作 size<=200 反序列化记得取正整数
        return bytes;
//        return mask.getBytes();
    }

    @Override
    public <T> T deserialize(byte[] bytes, TypeReference<T> typeReference) throws SerializationException {
        int bytesSize=bytes.length;
        int size =bytes[bytesSize-1]&0xFF;
        BooleanMask mask=new BooleanMask(size);
        for(int i=0;i<size;i++){
            mask.set(i,(bytes[i/8]&(1<<(i%8)))>0);
        }

        return (T) mask;
    }
}