package com.insure.ai.common.util;

import org.springframework.util.StringUtils;

/**
 * 敏感信息脱敏工具
 * <p>
 * 业务库里存的是明文证件号/手机号，"脱敏"必须在**输出环节**由代码保证，
 * 而不是依赖录入时人工打码——否则一旦对接真实业务库，全量敏感信息会直接下发到浏览器。
 * </p>
 * <p>
 * 本类只做展示层掩码，不参与存储。需要查看原文的后台场景请走独立的受控接口并记审计日志。
 * </p>
 *
 * @author insurance-ai
 */
public final class DesensitizeUtil {

    /** 掩码字符 */
    private static final String MASK = "*";

    private DesensitizeUtil() {
    }

    /**
     * 证件号脱敏：保留前 4 位与后 4 位
     * <p>330102199001011234 → 3301**********1234</p>
     */
    public static String idCard(String value) {
        return mask(value, 4, 4);
    }

    /**
     * 手机号脱敏：保留前 3 位与后 4 位
     * <p>13900000001 → 139****0001</p>
     */
    public static String phone(String value) {
        return mask(value, 3, 4);
    }

    /**
     * 姓名脱敏：保留首字，其余打码
     * <p>张三丰 → 张**</p>
     */
    public static String name(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String trimmed = value.trim();
        if (trimmed.length() == 1) {
            return trimmed;
        }
        return trimmed.charAt(0) + MASK.repeat(trimmed.length() - 1);
    }

    /**
     * 通用掩码：保留前 prefix 位、后 suffix 位，中间按实际长度打码
     * <p>长度不足以同时保留首尾时，整体打码，避免"脱敏后反而更好猜"。</p>
     */
    public static String mask(String value, int prefix, int suffix) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String trimmed = value.trim();
        int length = trimmed.length();
        if (length <= prefix + suffix) {
            return MASK.repeat(length);
        }
        return trimmed.substring(0, prefix)
                + MASK.repeat(length - prefix - suffix)
                + trimmed.substring(length - suffix);
    }
}
