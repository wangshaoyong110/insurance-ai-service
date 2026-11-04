package com.insure.ai.common.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.insure.ai.common.util.DesensitizeUtil;

import java.io.IOException;

/**
 * 证件号脱敏序列化器
 * <p>标注在实体字段上即可，所有出口（列表、详情、AI 工具返回）统一生效，不依赖业务代码自觉。</p>
 *
 * @author insurance-ai
 */
public class IdCardSerializer extends JsonSerializer<String> {

    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeString(DesensitizeUtil.idCard(value));
    }
}
