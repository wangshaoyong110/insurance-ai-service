package com.insure.ai.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 脱敏工具单元测试
 * <p>这里的断言是"对外承诺"：接口返回的证件号/手机号必须长这样，改动实现时不能悄悄放宽</p>
 */
@DisplayName("敏感信息脱敏工具")
class DesensitizeUtilTest {

    @Test
    @DisplayName("18 位身份证保留前 4 位与后 4 位")
    void shouldMaskIdCard() {
        assertThat(DesensitizeUtil.idCard("330102199001011234")).isEqualTo("3301**********1234");
    }

    @Test
    @DisplayName("手机号保留前 3 位与后 4 位")
    void shouldMaskPhone() {
        assertThat(DesensitizeUtil.phone("13900000001")).isEqualTo("139****0001");
    }

    @Test
    @DisplayName("姓名只保留首字")
    void shouldMaskName() {
        assertThat(DesensitizeUtil.name("张三")).isEqualTo("张*");
        assertThat(DesensitizeUtil.name("欧阳修")).isEqualTo("欧**");
    }

    @Test
    @DisplayName("单字姓名不做处理")
    void shouldKeepSingleCharName() {
        assertThat(DesensitizeUtil.name("张")).isEqualTo("张");
    }

    @Test
    @DisplayName("长度不足以同时保留首尾时整体打码，避免脱敏后反而更好猜")
    void shouldMaskEntirelyWhenTooShort() {
        assertThat(DesensitizeUtil.phone("1390000")).isEqualTo("*******");
        assertThat(DesensitizeUtil.idCard("3301")).isEqualTo("****");
    }

    @Test
    @DisplayName("空值与空白原样返回，不产生 NPE")
    void shouldReturnAsIsForBlank() {
        assertThat(DesensitizeUtil.idCard(null)).isNull();
        assertThat(DesensitizeUtil.phone("")).isEmpty();
        assertThat(DesensitizeUtil.name("   ")).isEqualTo("   ");
    }

    @Test
    @DisplayName("对已脱敏数据重复脱敏结果不变（幂等）")
    void shouldBeIdempotent() {
        String once = DesensitizeUtil.idCard("330102199001011234");
        assertThat(DesensitizeUtil.idCard(once)).isEqualTo(once);
    }

    @Test
    @DisplayName("首尾空格不影响脱敏结果")
    void shouldTrimBeforeMask() {
        assertThat(DesensitizeUtil.phone("  13900000001  ")).isEqualTo("139****0001");
    }
}
