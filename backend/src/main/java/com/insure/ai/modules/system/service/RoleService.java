package com.insure.ai.modules.system.service;

import com.insure.ai.modules.system.dto.RoleSaveDTO;
import com.insure.ai.modules.system.entity.SysRole;
import java.util.List;
import java.util.Map;

/**
 * 角色服务:角色维护与菜单授权
 *
 * @author insurance-ai
 */
public interface RoleService {

    /**
     * 角色列表(含账号数量)
     */
    List<SysRole> list();

    /**
     * 角色下拉选项(仅启用状态)
     */
    List<SysRole> options();

    /**
     * 角色详情:角色基础信息 + 已授权菜单ID
     */
    Map<String, Object> detail(Long id);

    /**
     * 新增或编辑角色,并覆盖式保存菜单授权
     */
    Long save(RoleSaveDTO dto);

    /**
     * 删除角色(内置角色与有账号关联的角色不可删除)
     */
    void delete(Long id);
}
