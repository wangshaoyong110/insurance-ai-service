package com.insure.ai.modules.system.service.impl;

import com.insure.ai.modules.system.service.SysUserService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.insure.ai.common.BusinessException;
import com.insure.ai.common.Constants;
import com.insure.ai.common.PageResult;
import com.insure.ai.common.ResultCode;
import com.insure.ai.common.enums.CommonStatusEnum;
import com.insure.ai.modules.system.dto.UserQuery;
import com.insure.ai.modules.system.dto.UserSaveDTO;
import com.insure.ai.modules.system.dto.UserVO;
import com.insure.ai.modules.system.entity.SysRole;
import com.insure.ai.modules.system.entity.SysUser;
import com.insure.ai.modules.system.mapper.SysRoleMapper;
import com.insure.ai.modules.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 管理端用户管理服务(仅操作 sys_user 表)
 *
 * @author insurance-ai
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl implements SysUserService {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * 分页查询管理端用户
     */
    @Override
    public PageResult<UserVO> page(UserQuery query) {
        Page<SysUser> page = new Page<>(query.getPageNum(), query.getPageSize());
        sysUserMapper.selectPage(page, Wrappers.<SysUser>lambdaQuery()
                .eq(query.getRoleId() != null, SysUser::getRoleId, query.getRoleId())
                .eq(query.getStatus() != null, SysUser::getStatus, query.getStatus())
                .and(StringUtils.hasText(query.getKeyword()), wrapper -> wrapper
                        .like(SysUser::getUsername, query.getKeyword())
                        .or().like(SysUser::getRealName, query.getKeyword())
                        .or().like(SysUser::getPhone, query.getKeyword()))
                .orderByAsc(SysUser::getId));
        PageResult<UserVO> result = PageResult.of(page, UserVO::from);
        // 批量补充角色名称,避免逐行查询
        Map<Long, SysRole> roleMap = sysRoleMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysRole::getId, Function.identity()));
        result.getRecords().forEach(item -> {
            SysRole role = roleMap.get(item.getRoleId());
            if (role != null) {
                item.setRoleName(role.getRoleName());
                item.setRoleCode(role.getRoleCode());
            }
        });
        return result;
    }

    /**
     * 查询管理端用户详情
     */
    @Override
    public UserVO detail(Long id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        SysRole role = user.getRoleId() == null ? null : sysRoleMapper.selectById(user.getRoleId());
        if (role != null) {
            user.setRoleName(role.getRoleName());
            user.setRoleCode(role.getRoleCode());
        }
        return UserVO.from(user);
    }

    /**
     * 新增或编辑管理端用户
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public Long save(UserSaveDTO dto) {
        boolean isCreate = dto.getId() == null;
        if (isCreate && !StringUtils.hasText(dto.getPassword())) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "新增用户必须设置密码");
        }
        SysUser exists = null;
        if (!isCreate) {
            exists = sysUserMapper.selectById(dto.getId());
            // 编辑场景先校验用户是否存在,避免返回误导性提示
            if (exists == null) {
                throw new BusinessException(ResultCode.USER_NOT_EXIST);
            }
        }
        // 角色必须存在
        SysRole role = sysRoleMapper.selectById(dto.getRoleId());
        if (role == null) {
            throw new BusinessException(ResultCode.ROLE_NOT_EXIST);
        }
        // 账号唯一性校验(编辑时排除自身)
        Long usernameCount = sysUserMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, dto.getUsername().trim())
                .ne(!isCreate, SysUser::getId, dto.getId()));
        if (usernameCount > 0) {
            throw new BusinessException(ResultCode.USER_NAME_EXIST);
        }
        // 手机号唯一性校验
        Long phoneCount = sysUserMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getPhone, dto.getPhone().trim())
                .ne(!isCreate, SysUser::getId, dto.getId()));
        if (phoneCount > 0) {
            throw new BusinessException(ResultCode.USER_PHONE_EXIST);
        }
        // 内置超管不允许被降级或禁用
        boolean builtInAdmin = exists != null && Constants.BUILT_IN_ADMIN.equals(exists.getUsername());

        SysUser user = new SysUser();
        user.setId(dto.getId());
        user.setUsername(dto.getUsername().trim());
        user.setRealName(dto.getRealName());
        user.setPhone(dto.getPhone().trim());
        user.setEmail(dto.getEmail());
        user.setAvatar(dto.getAvatar());
        user.setRoleId(builtInAdmin ? exists.getRoleId() : dto.getRoleId());
        user.setStatus(builtInAdmin ? CommonStatusEnum.ENABLE.getCode()
                : (dto.getStatus() == null ? CommonStatusEnum.ENABLE.getCode() : dto.getStatus()));
        user.setRemark(dto.getRemark());
        if (StringUtils.hasText(dto.getPassword())) {
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
        }
        if (isCreate) {
            sysUserMapper.insert(user);
        } else {
            sysUserMapper.updateById(user);
        }
        return user.getId();
    }

    /**
     * 删除管理端用户(逻辑删除,内置超管与当前登录账号不可删除)
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void delete(Long id, Long currentUserId) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        if (Constants.BUILT_IN_ADMIN.equals(user.getUsername()) || id.equals(currentUserId)) {
            throw new BusinessException(ResultCode.USER_CANNOT_DELETE_SELF);
        }
        sysUserMapper.deleteById(id);
    }

    /**
     * 重置密码为默认密码
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void resetPassword(Long id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        SysUser update = new SysUser();
        update.setId(id);
        update.setPassword(passwordEncoder.encode(Constants.DEFAULT_PASSWORD));
        sysUserMapper.updateById(update);
    }

    /**
     * 启用 / 禁用管理端用户
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void changeStatus(Long id, Integer status) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        if (Constants.BUILT_IN_ADMIN.equals(user.getUsername())
                && CommonStatusEnum.DISABLE.getCode().equals(status)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "内置超级管理员不可禁用");
        }
        SysUser update = new SysUser();
        update.setId(id);
        update.setStatus(status);
        sysUserMapper.updateById(update);
    }

    /**
     * 统计管理端账号数量
     */
    @Override
    public Long count() {
        return sysUserMapper.selectCount(null);
    }

    /**
     * 统计某个角色下的账号数量
     */
    @Override
    public Long countByRole(Long roleId) {
        return sysUserMapper.selectCount(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getRoleId, roleId));
    }

    /**
     * 查询全部角色(下拉选项用)
     */
    @Override
    public List<SysRole> listRoles() {
        return sysRoleMapper.selectList(Wrappers.<SysRole>lambdaQuery()
                .eq(SysRole::getStatus, CommonStatusEnum.ENABLE.getCode())
                .orderByAsc(SysRole::getSort));
    }
}
