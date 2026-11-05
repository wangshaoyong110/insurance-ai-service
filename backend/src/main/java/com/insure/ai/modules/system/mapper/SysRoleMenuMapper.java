package com.insure.ai.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.insure.ai.modules.system.entity.SysRoleMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 角色菜单关联 Mapper
 *
 * @author insurance-ai
 */
@Mapper
public interface SysRoleMenuMapper extends BaseMapper<SysRoleMenu> {

    /**
     * 物理删除某角色的全部菜单授权(重新授权前先清空,避免逻辑删除导致唯一键冲突)
     *
     * @param roleId 角色ID
     * @return 删除行数
     */
    int deleteByRoleId(@Param("roleId") Long roleId);
}
