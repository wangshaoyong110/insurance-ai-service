package com.insure.ai.modules.ai.tool;

import com.insure.ai.modules.policy.entity.InsClaim;
import com.insure.ai.modules.policy.entity.InsPolicy;
import com.insure.ai.modules.policy.service.PolicyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI 工具越权防护测试
 * <p>
 * 核心断言只有一句：<b>工具永远只用构造时注入的登录态 cusUserId 查询</b>，
 * 模型传进来的任何参数都不能变成查询条件的一部分。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AI 保单查询工具的越权防护")
class InsuranceQueryToolsTest {

    private static final Long LOGIN_USER_ID = 1L;

    @Mock
    private PolicyService policyService;

    private InsuranceQueryTools tools;

    @BeforeEach
    void setUp() {
        tools = new InsuranceQueryTools(policyService, LOGIN_USER_ID);
    }

    private InsPolicy policy(String policyNo, String productName) {
        InsPolicy policy = new InsPolicy();
        policy.setId(100L);
        policy.setPolicyNo(policyNo);
        policy.setProductName(productName);
        policy.setCoverageAmount(new BigDecimal("500000.00"));
        policy.setPremium(new BigDecimal("8600.00"));
        policy.setEffectiveDate(LocalDate.of(2023, 6, 15));
        policy.setExpireDate(LocalDate.of(2099, 12, 31));
        policy.setStatus(1);
        return policy;
    }

    @Test
    @DisplayName("查询保单清单只使用登录态用户ID，不接受模型传入的身份")
    void listPoliciesShouldUseLoginUserIdOnly() {
        when(policyService.listByCustomer(LOGIN_USER_ID))
                .thenReturn(List.of(policy("P202306150001", "守护一生终身重大疾病保险")));

        String result = tools.listMyPolicies();

        assertThat(result).contains("守护一生终身重大疾病保险");
        assertThat(result).contains("P202306150001");
        assertThat(result).contains("共 1 张保单");
        // 关键断言：查询条件只能是登录态 ID
        verify(policyService).listByCustomer(LOGIN_USER_ID);
        verify(policyService, never()).listByCustomer(2L);
    }

    @Test
    @DisplayName("查他人保单号时返回未找到，而不是把别人的数据吐出来")
    void detailShouldNotLeakOthersPolicy() {
        // 服务层按 (登录ID, 保单号) 联合过滤，查不到即返回 null
        when(policyService.findByCustomerAndPolicyNo(LOGIN_USER_ID, "P202501100003"))
                .thenReturn(null);

        String result = tools.getPolicyDetail("P202501100003");

        assertThat(result).doesNotContain("畅行无忧");
        assertThat(result).contains("未查询到保单号");
        verify(policyService).findByCustomerAndPolicyNo(LOGIN_USER_ID, "P202501100003");
    }

    @Test
    @DisplayName("查本人保单时正常返回详情")
    void detailShouldReturnOwnPolicy() {
        when(policyService.findByCustomerAndPolicyNo(LOGIN_USER_ID, "P202306150001"))
                .thenReturn(policy("P202306150001", "守护一生终身重大疾病保险"));

        String result = tools.getPolicyDetail("P202306150001");

        assertThat(result).contains("守护一生终身重大疾病保险");
        assertThat(result).contains("P202306150001");
    }

    @Test
    @DisplayName("理赔记录同样按登录态过滤")
    void claimsShouldUseLoginUserId() {
        InsClaim claim = new InsClaim();
        claim.setClaimNo("CLM20261025003");
        claim.setPolicyNo("P202403010002");
        claim.setClaimType("MEDICAL");
        claim.setClaimAmount(new BigDecimal("5600.00"));
        claim.setStatus(3);
        when(policyService.listClaimsByCustomer(LOGIN_USER_ID, null)).thenReturn(List.of(claim));

        String result = tools.listMyClaims(null);

        assertThat(result).contains("CLM20261025003");
        verify(policyService).listClaimsByCustomer(LOGIN_USER_ID, null);
    }

    @Test
    @DisplayName("服务层异常不会抛给模型，而是转成可读文案")
    void shouldSwallowServiceException() {
        when(policyService.listByCustomer(anyLong()))
                .thenThrow(new RuntimeException("数据库连接池耗尽"));

        String result = tools.listMyPolicies();

        assertThat(result).contains("暂时不可用");
        assertThat(result).doesNotContain("数据库连接池耗尽");
    }

    @Test
    @DisplayName("未登录（userId 为空）时不发起任何查询")
    void shouldNotQueryWhenAnonymous() {
        InsuranceQueryTools anonymous = new InsuranceQueryTools(policyService, null);

        String result = anonymous.listMyPolicies();

        assertThat(result).contains("未获取到当前登录客户");
        verify(policyService, never()).listByCustomer(anyLong());
        verify(policyService, never()).findByCustomerAndPolicyNo(anyLong(), anyString());
    }
}
