package com.insure.ai.modules.system.service.impl;

import com.insure.ai.modules.system.service.MenuService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.insure.ai.common.BusinessException;
import com.insure.ai.common.Constants;
import com.insure.ai.common.ResultCode;
import com.insure.ai.common.enums.CommonStatusEnum;
import com.insure.ai.common.enums.MenuTypeEnum;
import com.insure.ai.modules.system.dto.MenuSaveDTO;
import com.insure.ai.modules.system.entity.SysMenu;
import com.insure.ai.modules.system.entity.SysRole;
import com.insure.ai.modules.system.mapper.SysMenuMapper;
import com.insure.ai.modules.system.mapper.SysRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 菜单服务:菜单树构建、按角色授权过滤、菜单维护
 *
 * @author insurance-ai
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MenuServiceImpl implements MenuService {

    /** 顶级菜单的父级ID */
    private static final long ROOT_PARENT_ID = 0L;

    private final SysMenuMapper sysMenuMapper;
    private final SysRoleMapper sysRoleMapper;

    /**
     * 查询全部菜单树(菜单管理页使用)
     *
     * @param onlyEnabled 是否只返回启用菜单
     * @return 菜单树
     */
    @Override
    public List<SysMenu> tree(boolean onlyEnabled) {
        List<SysMenu> menus = sysMenuMapper.selectList(Wrappers.<SysMenu>lambdaQuery()
                .eq(onlyEnabled, SysMenu::getStatus, CommonStatusEnum.ENABLE.getCode())
                .orderByAsc(SysMenu::getSort)
                .orderByAsc(SysMenu::getId));
        return buildTree(menus);
    }

    /**
     * 查询某个角色可见的菜单树(用于后台导航)
     * <p>超级管理员默认拥有全部启用菜单,避免漏配菜单导致无法进入后台</p>
     *
     * @param roleCode 角色标识
     * @return 菜单树
     */
    @Override
    public List<SysMenu> treeByRoleCode(String roleCode) {
        List<SysMenu> enabled = sysMenuMapper.selectList(Wrappers.<SysMenu>lambdaQuery()
                .eq(SysMenu::getStatus, CommonStatusEnum.ENABLE.getCode())
                .ne(SysMenu::getMenuType, MenuTypeEnum.BUTTON.getCode())
                .orderByAsc(SysMenu::getSort)
                .orderByAsc(SysMenu::getId));
        if (Constants.ROLE_SUPER_ADMIN.equals(roleCode)) {
            return buildTree(enabled);
        }
        // 普通角色:仅返回已授权菜单,并自动补齐父级目录,避免子菜单失去父节点
        Set<Long> granted = loadGrantedMenuIds(roleCode);
        Map<Long, SysMenu> all = enabled.stream()
                .collect(Collectors.toMap(SysMenu::getId, Function.identity()));
        Set<Long> visibleIds = new HashSet<>();
        for (Long menuId : granted) {
            SysMenu current = all.get(menuId);
            while (current != null && visibleIds.add(current.getId())) {
                current = current.getParentId() == null || current.getParentId() == ROOT_PARENT_ID
                        ? null : all.get(current.getParentId());
            }
        }
        List<SysMenu> menus = enabled.stream().filter(item -> visibleIds.contains(item.getId())).toList();
        return buildTree(menus);
    }

    /**
     * 按角色查询已授权菜单ID
     *
     * @param roleCode 角色标识
     * @return 菜单ID集合
     */
    private Set<Long> loadGrantedMenuIds(String roleCode) {
        SysRole role = sysRoleMapper.selectOne(Wrappers.<SysRole>lambdaQuery()
                .eq(SysRole::getRoleCode, roleCode)
                .last("LIMIT 1"));
        if (role == null) {
            return Set.of();
        }
        return new HashSet<>(sysMenuMapper.selectMenuIdsByRoleId(role.getId()));
    }

    /**
     * 新增或编辑菜单
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public Long save(MenuSaveDTO dto) {
        boolean isCreate = dto.getId() == null;
        Long parentId = dto.getParentId() == null ? ROOT_PARENT_ID : dto.getParentId();
        if (parentId != null && parentId != ROOT_PARENT_ID) {
            SysMenu parent = sysMenuMapper.selectById(parentId);
            if (parent == null) {
                throw new BusinessException(ResultCode.MENU_NOT_EXIST);
            }
            // 编辑时不允许把父级指向自己或自己的子级,避免形成环
            if (!isCreate && isSelfOrDescendant(dto.getId(), parentId)) {
                throw new BusinessException(ResultCode.MENU_PARENT_ERROR);
            }
        }
        // 目录与菜单必须有路由地址(目录可只作为分组,但也建议填写),按钮无需路由
        if (!MenuTypeEnum.BUTTON.getCode().equals(dto.getMenuType())
                && !StringUtils.hasText(dto.getPath())) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "目录/菜单必须填写路由地址");
        }
        if (MenuTypeEnum.MENU.getCode().equals(dto.getMenuType())
                && !StringUtils.hasText(dto.getComponent())) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "菜单必须填写组件路径");
        }

        SysMenu menu = new SysMenu();
        menu.setId(dto.getId());
        menu.setParentId(parentId);
        menu.setMenuName(dto.getMenuName().trim());
        menu.setMenuType(dto.getMenuType());
        menu.setPath(dto.getPath());
        menu.setComponent(dto.getComponent());
        menu.setIcon(dto.getIcon());
        menu.setPerms(dto.getPerms());
        menu.setSort(dto.getSort() == null ? 0 : dto.getSort());
        menu.setVisible(dto.getVisible() == null ? CommonStatusEnum.ENABLE.getCode() : dto.getVisible());
        menu.setStatus(dto.getStatus() == null ? CommonStatusEnum.ENABLE.getCode() : dto.getStatus());
        menu.setRemark(dto.getRemark());
        if (isCreate) {
            sysMenuMapper.insert(menu);
        } else {
            sysMenuMapper.updateById(menu);
        }
        log.info("[菜单管理] {}菜单 id={} name={}", isCreate ? "新增" : "编辑", menu.getId(), menu.getMenuName());
        return menu.getId();
    }

    /**
     * 删除菜单(存在子菜单时禁止删除)
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void delete(Long id) {
        SysMenu menu = sysMenuMapper.selectById(id);
        if (menu == null) {
            throw new BusinessException(ResultCode.MENU_NOT_EXIST);
        }
        Long childCount = sysMenuMapper.selectCount(Wrappers.<SysMenu>lambdaQuery()
                .eq(SysMenu::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw new BusinessException(ResultCode.MENU_HAS_CHILDREN);
        }
        sysMenuMapper.deleteById(id);
        log.info("[菜单管理] 删除菜单 id={} name={}", id, menu.getMenuName());
    }

    /**
     * 判断 targetId 是否等于 menuId 或位于其子树上
     */
    private boolean isSelfOrDescendant(Long menuId, Long targetId) {
        if (menuId.equals(targetId)) {
            return true;
        }
        Set<Long> visited = new HashSet<>();
        Long cursor = targetId;
        while (cursor != null && cursor != ROOT_PARENT_ID && visited.add(cursor)) {
            SysMenu node = sysMenuMapper.selectById(cursor);
            if (node == null) {
                return false;
            }
            if (menuId.equals(node.getParentId())) {
                return true;
            }
            cursor = node.getParentId();
        }
        return false;
    }

    /**
     * 平铺列表构建为树(按 sort 升序)
     */
    private List<SysMenu> buildTree(List<SysMenu> menus) {
        Map<Long, SysMenu> map = menus.stream()
                .collect(Collectors.toMap(SysMenu::getId, Function.identity(), (a, b) -> a));
        List<SysMenu> roots = new ArrayList<>();
        for (SysMenu menu : menus) {
            menu.setChildren(new ArrayList<>());
        }
        for (SysMenu menu : menus) {
            Long parentId = menu.getParentId();
            SysMenu parent = parentId == null ? null : map.get(parentId);
            if (parent == null) {
                roots.add(menu);
            } else {
                parent.getChildren().add(menu);
            }
        }
        Comparator<SysMenu> comparator = Comparator
                .comparing((SysMenu item) -> item.getSort() == null ? 0 : item.getSort())
                .thenComparing(SysMenu::getId);
        roots.sort(comparator);
        roots.forEach(this::sortChildren);
        return roots;
    }

    /**
     * 递归排序子节点
     */
    private void sortChildren(SysMenu menu) {
        if (menu.getChildren() == null || menu.getChildren().isEmpty()) {
            return;
        }
        menu.getChildren().sort(Comparator
                .comparing((SysMenu item) -> item.getSort() == null ? 0 : item.getSort())
                .thenComparing(SysMenu::getId));
        menu.getChildren().forEach(this::sortChildren);
    }
}
