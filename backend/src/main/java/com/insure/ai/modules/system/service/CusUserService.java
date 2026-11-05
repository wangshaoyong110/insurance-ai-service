package com.insure.ai.modules.system.service;

import com.insure.ai.common.PageResult;
import com.insure.ai.modules.system.dto.CusUserQuery;
import com.insure.ai.modules.system.dto.CusUserVO;
import java.util.List;
import java.util.Map;

/**
 * 后台客户管理服务(操作 cus_user 表,与管理员账号隔离)
 *
 * @author insurance-ai
 */
public interface CusUserService {

    /**
     * 分页查询客户,并补充会话数量
     */
    PageResult<CusUserVO> page(CusUserQuery query);

    /**
     * 客户详情
     */
    CusUserVO detail(Long id);

    /**
     * 启用 / 禁用客户账号
     */
    void changeStatus(Long id, Integer status);

    /**
     * 重置客户密码为默认密码
     */
    void resetPassword(Long id);

    /**
     * 删除客户(逻辑删除,同时清空该客户的会话)
     */
    void delete(Long id);

    /**
     * 统计客户数量
     */
    Long count();

    /**
     * 统计启用状态的客户数量
     */
    Long countEnabled();

    /**
     * 今日新增客户数量
     */
    Long countToday();
}
