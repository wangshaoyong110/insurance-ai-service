package com.insure.ai.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 证件号校验与打码单测
 * <p>身份证带校验位,格式对不代表号码合法——这里锁死校验位与出生日期的判定,
 * 避免"随便填 18 位数字就能过"。</p>
 *
 * @author shaoyong.wang
 */
@DisplayName("证件号校验与打码")
class CertNoUtilsTest {

    /** 公开的合法测试身份证号(校验位正确) */
    private static final String VALID_ID_CARD = "11010519491231002X";

    @Test
    @DisplayName("合法身份证号通过校验")
    void validIdCardShouldPass() {
        assertThat(CertNoUtils.isValid("ID_CARD", VALID_ID_CARD)).isTrue();
        // 末位小写 x 也应接受
        assertThat(CertNoUtils.isValid("ID_CARD", "11010519491231002x")).isTrue();
    }

    @Test
    @DisplayName("校验位错误的身份证号被拒绝")
    void wrongCheckDigitShouldFail() {
        assertThat(CertNoUtils.isValid("ID_CARD", "110105194912310021")).isFalse();
    }

    @Test
    @DisplayName("位数不足或出生日期非法的身份证号被拒绝")
    void malformedIdCardShouldFail() {
        assertThat(CertNoUtils.isValid("ID_CARD", "1101051949123100")).isFalse();
        // 月份 13 非法
        assertThat(CertNoUtils.isValid("ID_CARD", "11010519491331002X")).isFalse();
    }

    @Test
    @DisplayName("护照/港澳台/外国人居留证按各自规则校验")
    void otherCertTypesShouldFollowOwnRules() {
        assertThat(CertNoUtils.isValid("PASSPORT", "E12345678")).isTrue();
        assertThat(CertNoUtils.isValid("PASSPORT", "12345678")).isFalse();
        assertThat(CertNoUtils.isValid("HK_MACAO", "H12345678")).isTrue();
        assertThat(CertNoUtils.isValid("TAIWAN", "12345678")).isTrue();
        assertThat(CertNoUtils.isValid("FOREIGN_RESIDENCE", "ABC123456789012")).isTrue();
        assertThat(CertNoUtils.isValid("OFFICER", "军字第123456号")).isTrue();
    }

    @Test
    @DisplayName("未知识别类型一律不通过")
    void unknownTypeShouldFail() {
        assertThat(CertNoUtils.isValid("UNKNOWN", VALID_ID_CARD)).isFalse();
        assertThat(CertNoUtils.isValid(null, VALID_ID_CARD)).isFalse();
        assertThat(CertNoUtils.isValid("ID_CARD", "  ")).isFalse();
    }

    @Test
    @DisplayName("打码保留前 3 后 2,不足 6 位整体隐藏")
    void maskShouldKeepHeadAndTail() {
        assertThat(CertNoUtils.mask(VALID_ID_CARD)).isEqualTo("110****2X");
        assertThat(CertNoUtils.mask("E12345678")).isEqualTo("E12****78");
        assertThat(CertNoUtils.mask("12345")).isEqualTo("***");
        assertThat(CertNoUtils.mask(null)).isEmpty();
    }
}
