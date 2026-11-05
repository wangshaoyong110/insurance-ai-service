package com.insure.ai.common.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 投保证件类型枚举
 * <p>覆盖保险行业投保时可接受的全部证件类型,证件号格式校验规则见 {@link com.insure.ai.common.util.CertNoUtils}</p>
 *
 * @author shaoyong.wang
 */
@Getter
public enum CertTypeEnum {

    /** 居民身份证 */
    ID_CARD("ID_CARD", "居民身份证"),
    /** 护照 */
    PASSPORT("PASSPORT", "护照"),
    /** 军官证 */
    OFFICER("OFFICER", "军官证"),
    /** 士兵证 */
    SOLDIER("SOLDIER", "士兵证"),
    /** 港澳居民来往内地通行证 */
    HK_MACAO("HK_MACAO", "港澳居民来往内地通行证"),
    /** 台湾居民来往大陆通行证 */
    TAIWAN("TAIWAN", "台湾居民来往大陆通行证"),
    /** 外国人永久居留身份证 */
    FOREIGN_RESIDENCE("FOREIGN_RESIDENCE", "外国人永久居留身份证");

    private final String code;
    private final String desc;

    CertTypeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 严格解析:非法值返回 empty,由调用方决定报错,不做静默回落
     */
    public static Optional<CertTypeEnum> parse(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(item -> item.code.equalsIgnoreCase(code.trim()))
                .findFirst();
    }

    /**
     * 全量列表(前端下拉用)
     */
    public static List<CertTypeEnum> all() {
        return Arrays.asList(values());
    }

    public static String descOf(String code) {
        return parse(code).map(CertTypeEnum::getDesc).orElse("未知证件");
    }
}
