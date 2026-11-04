package com.insure.ai.common.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;

/**
 * 保险业务分类枚举
 * <p>用于知识库分类与前端快捷提问,避免页面与后端出现魔法字符串</p>
 *
 * @author insurance-ai
 */
@Getter
public enum InsuranceCategoryEnum {

    /** 投保 */
    INSURE("INSURE", "投保", "投保需要准备什么材料?投保后多久生效?"),
    /** 保全 */
    POLICY("POLICY", "保全", "保单信息可以变更哪些内容?怎么做保全?"),
    /** 退保 */
    SURRENDER("SURRENDER", "退保", "退保能退回多少钱?犹豫期是多久?"),
    /** 减保 */
    REDUCE("REDUCE", "减保", "减保怎么计算?一年可以减保几次?"),
    /** 理赔 */
    CLAIM("CLAIM", "理赔", "理赔需要哪些材料?多久能到账?"),
    /** 其他 */
    OTHER("OTHER", "其他", "如何联系人工客服?");

    private final String code;
    private final String desc;
    /** 前端快捷提问示例 */
    private final String sampleQuestion;

    InsuranceCategoryEnum(String code, String desc, String sampleQuestion) {
        this.code = code;
        this.desc = desc;
        this.sampleQuestion = sampleQuestion;
    }

    public static InsuranceCategoryEnum of(String code) {
        return Arrays.stream(values())
                .filter(item -> item.code.equalsIgnoreCase(code))
                .findFirst()
                .orElse(OTHER);
    }

    public static String descOf(String code) {
        return of(code).getDesc();
    }

    public static List<InsuranceCategoryEnum> all() {
        return Arrays.asList(values());
    }
}
