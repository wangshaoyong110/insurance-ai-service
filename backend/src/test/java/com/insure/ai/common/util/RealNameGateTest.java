package com.insure.ai.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 实名认证问答闸门单测
 * <p>两个方向都要锁:查本人业务数据必须拦(否则未实名也能看保单),
 * 通用知识问题不能误拦(否则客户一问退保规则就被要求认证,体验很差)。</p>
 *
 * @author shaoyong.wang
 */
@DisplayName("实名认证问答闸门")
class RealNameGateTest {

    @Test
    @DisplayName("查询本人保单/理赔/缴费的问题必须实名")
    void personalDataQuestionsShouldRequireRealName() {
        assertThat(RealNameGate.requiresRealName("我的保单什么时候到期")).isTrue();
        assertThat(RealNameGate.requiresRealName("帮我看下我名下的理赔进度")).isTrue();
        assertThat(RealNameGate.requiresRealName("我买了什么保险")).isTrue();
        assertThat(RealNameGate.requiresRealName("我是不是忘了交保费")).isTrue();
        assertThat(RealNameGate.requiresRealName("保单号 P202403010002 现在什么状态")).isTrue();
        assertThat(RealNameGate.requiresRealName("查一下我的保额")).isTrue();
    }

    @Test
    @DisplayName("通用知识问题不拦:投保规则、退保计算、理赔材料")
    void knowledgeQuestionsShouldPass() {
        assertThat(RealNameGate.requiresRealName("退保能退回多少钱")).isFalse();
        assertThat(RealNameGate.requiresRealName("等待期是多久")).isFalse();
        assertThat(RealNameGate.requiresRealName("理赔需要准备哪些材料")).isFalse();
        assertThat(RealNameGate.requiresRealName("犹豫期可以全额退保吗")).isFalse();
        assertThat(RealNameGate.requiresRealName("如何变更受益人")).isFalse();
    }

    @Test
    @DisplayName("空内容不拦")
    void blankShouldPass() {
        assertThat(RealNameGate.requiresRealName(null)).isFalse();
        assertThat(RealNameGate.requiresRealName("   ")).isFalse();
    }

    @Test
    @DisplayName("引导话术包含实名认证与通用问题可继续提问的说明")
    void guidanceShouldExplainBothPaths() {
        assertThat(RealNameGate.guidance()).contains("实名认证");
        assertThat(RealNameGate.guidance()).contains("通用规则");
    }
}
