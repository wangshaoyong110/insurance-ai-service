package com.insure.ai.common.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.insure.ai.common.util.DesensitizeUtil;

import java.io.IOException;

/**
 * 手机号脱敏序列化器
 *
 * @author insurance-ai
 */
public class PhoneSerializer extends JsonSerializer<String> {

    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeString(DesensitizeUtil.phone(value));
    }
}
