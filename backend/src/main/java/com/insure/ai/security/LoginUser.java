package com.insure.ai.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 登录用户信息(存放于 SecurityContext)
 * <p>管理端与客户端共用该对象,通过 userType 区分:</p>
 * <ul>
 *     <li>ADMIN:授权 ROLE_ADMIN + ROLE_{roleCode}(如 ROLE_SUPER_ADMIN)</li>
 *     <li>CUSTOMER:仅授权 ROLE_CUSTOMER,无法访问 /admin/** 接口</li>
 * </ul>
 *
 * @author insurance-ai
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginUser implements UserDetails, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 角色前缀(Spring Security 约定) */
    public static final String ROLE_PREFIX = "ROLE_";

    /** 用户ID(管理端为 sys_user.id,客户端为 cus_user.id) */
    private Long id;
    /** 登录账号 */
    private String username;
    /** 密码(BCrypt 密文,序列化时忽略) */
    @JsonIgnore
    private String password;
    /** 真实姓名 / 昵称 */
    private String realName;
    /** 手机号 */
    private String phone;
    /** 用户类型:ADMIN-管理端 CUSTOMER-客户端 */
    private String userType;
    /** 角色标识(仅管理端有值,如 SUPER_ADMIN / OPERATOR) */
    private String roleCode;
    /** 状态:1-启用 0-禁用 */
    private Integer status;

    /**
     * 权限集合:端类型角色 + 管理端业务角色
     */
    @Override
    @JsonIgnore
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> authorities = new ArrayList<>(2);
        authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + userType));
        if (roleCode != null && !roleCode.isBlank()) {
            authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + roleCode));
        }
        return authorities;
    }

    /**
     * 是否管理端账号
     */
    @JsonIgnore
    public boolean isAdminUser() {
        return "ADMIN".equals(userType);
    }

    @Override
    @JsonIgnore
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    @JsonIgnore
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    @JsonIgnore
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    @JsonIgnore
    public boolean isEnabled() {
        return status != null && status == 1;
    }
}
