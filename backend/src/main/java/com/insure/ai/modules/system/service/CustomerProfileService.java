package com.insure.ai.modules.system.service;

import java.util.List;
import java.util.Map;

/**
 * 客户 360 视图
 * <p>
 * 坐席处理工单时最缺的是上下文：这个客户买过什么、有没有在理赔、之前聊过什么。
 * 原先后台客户列表只有账号信息，点进去也看不到业务数据，只能再切到保单页按姓名搜一遍。
 * 这里把四块数据一次性聚合出来。
 * </p>
 *
 * @author insurance-ai
 */
public interface CustomerProfileService {

    /**
     * 客户全景信息
     */
    Map<String, Object> profile(Long cusUserId);
}
