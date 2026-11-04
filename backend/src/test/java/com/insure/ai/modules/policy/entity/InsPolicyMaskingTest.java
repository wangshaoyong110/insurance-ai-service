package com.insure.ai.modules.policy.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 敏感字段出口脱敏测试
 * <p>
 * 这条断言比业务逻辑测试更重要：它保证"库里是明文、接口返回必须是掩码"这件事
 * 由代码强制，而不是依赖录入数据时人工打码。删掉实体上的序列化注解，这个测试会直接失败。
 * </p>
 */
@DisplayName("保单实体序列化脱敏")
class InsPolicyMaskingTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    @DisplayName("证件号与手机号在 JSON 出口被强制打码")
    void shouldMaskOnSerialization() throws Exception {
        InsPolicy policy = new InsPolicy();
        policy.setPolicyNo("P202306150001");
        policy.setInsuredIdCard("330102199001011234");
        policy.setCustomerPhone("13900000001");

        String json = objectMapper.writeValueAsString(policy);

        assertThat(json).contains("\"insuredIdCard\":\"3301**********1234\"");
        assertThat(json).contains("\"customerPhone\":\"139****0001\"");
        assertThat(json).doesNotContain("330102199001011234");
        assertThat(json).doesNotContain("13900000001");
    }

    @Test
    @DisplayName("字段为空时不影响序列化")
    void shouldSerializeNullFields() throws Exception {
        InsPolicy policy = new InsPolicy();
        policy.setPolicyNo("P202306150001");

        String json = objectMapper.writeValueAsString(policy);

        assertThat(json).contains("\"insuredIdCard\":null");
        assertThat(json).contains("\"customerPhone\":null");
    }
}
