package com.insure.ai.modules.system.service;

import com.insure.ai.common.PageResult;
import com.insure.ai.modules.system.dto.UserQuery;
import com.insure.ai.modules.system.dto.UserSaveDTO;
import com.insure.ai.modules.system.dto.UserVO;
import com.insure.ai.modules.system.entity.SysRole;
import java.util.List;
import java.util.Map;

/**
 * 管理端用户管理服务(仅操作 sys_user 表)
 *
 * @author insurance-ai
 */
public interface SysUserService {

    /**
     * 分页查询管理端用户
     */
    PageResult<UserVO> page(UserQuery query);

    /**
     * 查询管理端用户详情
     */
    UserVO detail(Long id);

    /**
     * 新增或编辑管理端用户
     */
    Long save(UserSaveDTO dto);

    /**
     * 删除管理端用户(逻辑删除,内置超管与当前登录账号不可删除)
     */
    void delete(Long id, Long currentUserId);

    /**
     * 重置密码为默认密码
     */
    void resetPassword(Long id);

    /**
     * 启用 / 禁用管理端用户
     */
    void changeStatus(Long id, Integer status);

    /**
     * 统计管理端账号数量
     */
    Long count();

    /**
     * 统计某个角色下的账号数量
     */
    Long countByRole(Long roleId);

    /**
     * 查询全部角色(下拉选项用)
     */
    List<SysRole> listRoles();
}
