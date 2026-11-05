package com.insure.ai.common.util;

import com.insure.ai.common.enums.CertTypeEnum;

import java.util.regex.Pattern;

/**
 * 证件号格式校验与打码工具
 * <p>
 * 按证件类型校验格式:身份证含出生日期与校验位(GB 11643-1999),
 * 其余证件按出入境证件通行位数与字符集校验。
 * 只做格式校验,真实有效性由实名认证通道(运营商/公安)负责。
 * </p>
 *
 * @author shaoyong.wang
 */
public final class CertNoUtils {

    /** 居民身份证:6 位地址码 + 8 位出生日期 + 3 位顺序码 + 1 位校验码 */
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("^[0-9]{17}[0-9Xx]$");
    /** 护照:字母开头,字母数字组合,5-16 位 */
    private static final Pattern PASSPORT_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9]{4,15}$");
    /** 港澳居民来往内地通行证:H/M + 8 位或 10 位数字 */
    private static final Pattern HK_MACAO_PATTERN = Pattern.compile("^[HMhm][0-9]{8,10}$");
    /** 台湾居民来往大陆通行证:8 位数字或"台胞证"8 位数字 + 2 位次数 */
    private static final Pattern TAIWAN_PATTERN = Pattern.compile("^[0-9]{8}([0-9]{2})?$");
    /** 军官证/士兵证:汉字"军""士"等开头或纯数字编号,长度 6-12 */
    private static final Pattern MILITARY_PATTERN = Pattern.compile("^[\\u4e00-\\u9fa5A-Za-z0-9]{6,12}$");
    /** 外国人永久居留身份证:15 位,3 位字母 + 12 位数字 */
    private static final Pattern FOREIGN_RESIDENCE_PATTERN = Pattern.compile("^[A-Za-z]{3}[0-9]{12}$");

    /** 身份证校验位加权因子(Wi) */
    private static final int[] ID_CARD_WEIGHTS = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
    /** 身份证校验位映射表 */
    private static final char[] ID_CARD_CHECK_CODES = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};

    private CertNoUtils() {
    }

    /**
     * 校验证件号与证件类型是否匹配
     *
     * @param certType 证件类型编码,见 {@link CertTypeEnum}
     * @param certNo   证件号
     * @return true 格式正确
     */
    public static boolean isValid(String certType, String certNo) {
        if (certNo == null || certNo.isBlank()) {
            return false;
        }
        CertTypeEnum type = CertTypeEnum.parse(certType).orElse(null);
        if (type == null) {
            return false;
        }
        String no = certNo.trim();
        return switch (type) {
            case ID_CARD -> isValidIdCard(no);
            case PASSPORT -> PASSPORT_PATTERN.matcher(no).matches();
            case OFFICER, SOLDIER -> MILITARY_PATTERN.matcher(no).matches();
            case HK_MACAO -> HK_MACAO_PATTERN.matcher(no).matches();
            case TAIWAN -> TAIWAN_PATTERN.matcher(no).matches();
            case FOREIGN_RESIDENCE -> FOREIGN_RESIDENCE_PATTERN.matcher(no).matches();
        };
    }

    /**
     * 居民身份证校验:位数 + 出生日期 + 校验位
     */
    private static boolean isValidIdCard(String no) {
        if (!ID_CARD_PATTERN.matcher(no).matches()) {
            return false;
        }
        // 出生日期段必须是合法日期:yyyyMMdd
        String birth = no.substring(6, 14);
        int year = Integer.parseInt(birth.substring(0, 4));
        int month = Integer.parseInt(birth.substring(4, 6));
        int day = Integer.parseInt(birth.substring(6, 8));
        if (year < 1900 || month < 1 || month > 12 || day < 1 || day > 31) {
            return false;
        }
        // 校验位:Σ(digit × Wi) mod 11 映射到校验码
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            sum += (no.charAt(i) - '0') * ID_CARD_WEIGHTS[i];
        }
        char expected = ID_CARD_CHECK_CODES[sum % 11];
        return Character.toUpperCase(no.charAt(17)) == expected;
    }

    /**
     * 证件号打码:保留前 3 后 2,中间用 4 个 * 占位
     * <p>不足 6 位的证件号整体替换为 ***,避免短证件泄露过多</p>
     */
    public static String mask(String certNo) {
        if (certNo == null || certNo.isBlank()) {
            return "";
        }
        String no = certNo.trim();
        if (no.length() < 6) {
            return "***";
        }
        return no.substring(0, 3) + "****" + no.substring(no.length() - 2);
    }
}
