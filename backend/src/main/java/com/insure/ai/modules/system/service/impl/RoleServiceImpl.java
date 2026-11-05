package com.insure.ai.modules.system.service.impl;

import com.insure.ai.modules.system.service.RoleService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.insure.ai.common.BusinessException;
import com.insure.ai.common.Constants;
import com.insure.ai.common.ResultCode;
import com.insure.ai.common.enums.CommonStatusEnum;
import com.insure.ai.modules.system.dto.RoleSaveDTO;
import com.insure.ai.modules.system.entity.SysRole;
import com.insure.ai.modules.system.entity.SysRoleMenu;
import com.insure.ai.modules.system.mapper.SysMenuMapper;
import com.insure.ai.modules.system.mapper.SysRoleMapper;
import com.insure.ai.modules.system.mapper.SysRoleMenuMapper;
import com.insure.ai.modules.system.mapper.SysUserMapper;
import com.insure.ai.modules.system.entity.SysUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 角色服务:角色维护与菜单授权
 *
 * @author insurance-ai
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final SysRoleMapper sysRoleMapper;
    private final SysRoleMenuMapper sysRoleMenuMapper;
    private final SysMenuMapper sysMenuMapper;
    private final SysUserMapper sysUserMapper;

    /**
     * 角色列表(含账号数量)
     */
    @Override
    public List<SysRole> list() {
        List<SysRole> roles = sysRoleMapper.selectList(Wrappers.<SysRole>lambdaQuery()
                .orderByAsc(SysRole::getSort)
                .orderByAsc(SysRole::getId));
        roles.forEach(role -> role.setUserCount(sysUserMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getRoleId, role.getId()))));
        return roles;
    }

    /**
     * 角色下拉选项(仅启用状态)
     */
    @Override
    public List<SysRole> options() {
        return sysRoleMapper.selectList(Wrappers.<SysRole>lambdaQuery()
                .eq(SysRole::getStatus, CommonStatusEnum.ENABLE.getCode())
                .orderByAsc(SysRole::getSort));
    }

    /**
     * 角色详情:角色基础信息 + 已授权菜单ID
     */
    @Override
    public Map<String, Object> detail(Long id) {
        SysRole role = sysRoleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(ResultCode.ROLE_NOT_EXIST);
        }
        Map<String, Object> data = new HashMap<>(4);
        data.put("role", role);
        data.put("menuIds", sysMenuMapper.selectMenuIdsByRoleId(id));
        return data;
    }

    /**
     * 新增或编辑角色,并覆盖式保存菜单授权
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public Long save(RoleSaveDTO dto) {
        boolean isCreate = dto.getId() == null;
        if (!isCreate) {
            SysRole exists = sysRoleMapper.selectById(dto.getId());
            if (exists == null) {
                throw new BusinessException(ResultCode.ROLE_NOT_EXIST);
            }
            if (Constants.ROLE_SUPER_ADMIN.equals(exists.getRoleCode())) {
                throw new BusinessException(ResultCode.ROLE_BUILT_IN);
            }
        }
        // 角色标识唯一
        Long count = sysRoleMapper.selectCount(Wrappers.<SysRole>lambdaQuery()
                .eq(SysRole::getRoleCode, dto.getRoleCode())
                .ne(!isCreate, SysRole::getId, dto.getId()));
        if (count > 0) {
            throw new BusinessException(ResultCode.ROLE_CODE_EXIST);
        }
        SysRole role = new SysRole();
        role.setId(dto.getId());
        role.setRoleCode(dto.getRoleCode());
        role.setRoleName(dto.getRoleName());
        role.setSort(dto.getSort() == null ? 0 : dto.getSort());
        role.setStatus(dto.getStatus() == null ? CommonStatusEnum.ENABLE.getCode() : dto.getStatus());
        role.setRemark(dto.getRemark());
        if (isCreate) {
            sysRoleMapper.insert(role);
        } else {
            sysRoleMapper.updateById(role);
        }
        // 覆盖式授权:先物理清空,再按本次勾选写入
        if (dto.getMenuIds() != null) {
            sysRoleMenuMapper.deleteByRoleId(role.getId());
            for (Long menuId : dto.getMenuIds().stream().distinct().toList()) {
                SysRoleMenu relation = new SysRoleMenu();
                relation.setRoleId(role.getId());
                relation.setMenuId(menuId);
                sysRoleMenuMapper.insert(relation);
            }
        }
        log.info("[角色管理] {}角色 id={} code={} 授权菜单数={}", isCreate ? "新增" : "编辑",
                role.getId(), role.getRoleCode(),
                dto.getMenuIds() == null ? 0 : dto.getMenuIds().size());
        return role.getId();
    }

    /**
     * 删除角色(内置角色与有账号关联的角色不可删除)
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void delete(Long id) {
        SysRole role = sysRoleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(ResultCode.ROLE_NOT_EXIST);
        }
        if (Constants.ROLE_SUPER_ADMIN.equals(role.getRoleCode())) {
            throw new BusinessException(ResultCode.ROLE_BUILT_IN);
        }
        Long userCount = sysUserMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getRoleId, id));
        if (userCount != null && userCount > 0) {
            throw new BusinessException(ResultCode.ROLE_IN_USE);
        }
        sysRoleMapper.deleteById(id);
        sysRoleMenuMapper.deleteByRoleId(id);
        log.info("[角色管理] 删除角色 id={} code={}", id, role.getRoleCode());
    }
}
