package com.insure.ai.modules.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.insure.ai.modules.system.entity.SysMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 菜单 Mapper
 *
 * @author insurance-ai
 */
@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenu> {

    /**
     * 查询某个角色已授权的菜单ID集合
     *
     * @param roleId 角色ID
     * @return 菜单ID列表
     */
    @Select("""
            SELECT rm.menu_id
            FROM sys_role_menu rm
            WHERE rm.role_id = #{roleId}
              AND rm.deleted = 0
            """)
    List<Long> selectMenuIdsByRoleId(@Param("roleId") Long roleId);
}
