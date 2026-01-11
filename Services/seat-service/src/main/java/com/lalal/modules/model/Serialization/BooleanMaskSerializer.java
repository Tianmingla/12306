package com.lalal.modules.model.Serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.lalal.modules.model.BooleanMask;

import java.io.IOException;

public class BooleanMaskSerializer extends JsonSerializer<BooleanMask> {
    @Override
    public void serialize(BooleanMask mask, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mask.getSize(); i++) {
            sb.append(mask.get(i) ? '1' : '0');
        }
        gen.writeRaw(sb.toString());
    }
//    @Override
//    public void serializeWithType(
//            BooleanMask value,
//            JsonGenerator gen,
//            SerializerProvider serializers,
//            TypeSerializer typeSer) throws IOException {
//        // 我们不使用 type info，直接按普通方式序列化
//        StringBuilder sb = new StringBuilder();
//        for (int i = 0; i < value.getSize(); i++) {
//            sb.append(value.get(i) ? '1' : '0');
//        }
//        typeSer.writeTypePrefix(gen, typeSer.typeId(value, JsonToken.START_OBJECT));
//        gen.writeStringField("mask", sb.toString());
//        typeSer.writeTypeSuffix(gen, typeSer.typeId(value, JsonToken.START_OBJECT));
////        serialize(value, gen, serializers);
//    }
}