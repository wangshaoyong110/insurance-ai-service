package com.insure.ai.modules.system.service;

import com.insure.ai.modules.system.dto.MenuSaveDTO;
import com.insure.ai.modules.system.entity.SysMenu;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 菜单服务:菜单树构建、按角色授权过滤、菜单维护
 *
 * @author insurance-ai
 */
public interface MenuService {

    /**
     * 查询全部菜单树(菜单管理页使用)
     *
     * @param onlyEnabled 是否只返回启用菜单
     * @return 菜单树
     */
    List<SysMenu> tree(boolean onlyEnabled);

    /**
     * 查询某个角色可见的菜单树(用于后台导航)
     * <p>超级管理员默认拥有全部启用菜单,避免漏配菜单导致无法进入后台</p>
     *
     * @param roleCode 角色标识
     * @return 菜单树
     */
    List<SysMenu> treeByRoleCode(String roleCode);

    /**
     * 新增或编辑菜单
     */
    Long save(MenuSaveDTO dto);

    /**
     * 删除菜单(存在子菜单时禁止删除)
     */
    void delete(Long id);
}
