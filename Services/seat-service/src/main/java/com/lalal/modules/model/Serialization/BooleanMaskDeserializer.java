package com.lalal.modules.model.Serialization;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.lalal.modules.model.BooleanMask;

import java.io.IOException;
import java.util.BitSet;

public class BooleanMaskDeserializer extends JsonDeserializer<BooleanMask> {
    @Override
    public BooleanMask deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String bitString = p.getValueAsString();
        if (bitString == null) {
            throw ctxt.mappingException("Expected a bit string like '0101', got null");
        }
        int size = bitString.length();
        BitSet bits = new BitSet(size);

        for (int i = 0; i < size; i++) {
            char c = bitString.charAt(i);
            if (c == '1') {
                bits.set(i);
            } else if (c != '0') {
                throw ctxt.mappingException("Invalid character in bit string at index %d: '%c'. Only '0' and '1' allowed.", i, c);
            }
        }

        return new BooleanMask(size, bits);
    }
}