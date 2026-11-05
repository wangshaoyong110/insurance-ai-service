package com.insure.ai.modules.chat.support;

import com.insure.ai.modules.system.entity.CusUser;
import com.insure.ai.modules.system.mapper.CusUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 客户实名状态判定
 * <p>
 * 对话链路里有两处需要它:决定是否直接给实名引导话术、决定工具层是否放行保单/理赔查询。
 * 抽成独立组件后,发送服务不必再直接依赖客户表。
 * </p>
 *
 * @author insurance-ai
 */
@Component
@RequiredArgsConstructor
public class RealNameVerifier {

    private final CusUserMapper cusUserMapper;

    /**
     * 当前客户是否已完成实名认证
     * <p>未实名客户只能问通用知识;涉及本人保单/理赔/缴费的问题会被引导去认证。</p>
     */
    public boolean isVerified(Long cusUserId) {
        if (cusUserId == null) {
            return false;
        }
        CusUser user = cusUserMapper.selectById(cusUserId);
        return user != null && user.getRealVerified() != null && user.getRealVerified() == 1;
    }
}
