package com.insure.ai.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JWT 签发与校验单元测试
 * <p>token 是唯一的身份凭据，任何"解析失败却放行"的回归都是重大事故，因此单独立测</p>
 */
@DisplayName("JWT 签发与解析")
class JwtUtilsTest {

    private static final String SECRET = "unit-test-jwt-secret-key-at-least-32-bytes-long";

    private JwtUtils jwtUtils;

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "secret", SECRET);
        ReflectionTestUtils.setField(jwtUtils, "expire", 3_600_000L);
        ReflectionTestUtils.setField(jwtUtils, "prefix", "Bearer ");
        ReflectionTestUtils.setField(jwtUtils, "header", "Authorization");
        jwtUtils.init();
    }

    private LoginUser sampleUser() {
        LoginUser user = new LoginUser();
        user.setId(1L);
        user.setUsername("13900000001");
        user.setRealName("张三");
        user.setPhone("13900000001");
        user.setUserType("CUSTOMER");
        user.setRoleCode(null);
        user.setStatus(1);
        return user;
    }

    @Test
    @DisplayName("签发的 token 是三段式，且能解析回原始身份")
    void shouldGenerateAndParse() {
        String token = jwtUtils.generateToken(sampleUser());

        assertThat(token).hasSizeGreaterThan(20);
        assertThat(token.split("\\.")).hasSize(3);

        LoginUser parsed = jwtUtils.toLoginUser(token);
        assertThat(parsed).isNotNull();
        assertThat(parsed.getId()).isEqualTo(1L);
        assertThat(parsed.getUsername()).isEqualTo("13900000001");
        assertThat(parsed.getUserType()).isEqualTo("CUSTOMER");
    }

    @Test
    @DisplayName("已过期的 token 解析失败，不能当作有效身份")
    void shouldRejectExpiredToken() {
        ReflectionTestUtils.setField(jwtUtils, "expire", -60_000L);
        String expired = jwtUtils.generateToken(sampleUser());

        assertThat(jwtUtils.parseToken(expired)).isNull();
    }

    @Test
    @DisplayName("被篡改的 token 解析失败")
    void shouldRejectTamperedToken() {
        String token = jwtUtils.generateToken(sampleUser());
        // 改掉签名段的最后一个字符
        String tampered = token.substring(0, token.length() - 1)
                + (token.endsWith("A") ? "B" : "A");

        assertThat(jwtUtils.parseToken(tampered)).isNull();
    }

    @Test
    @DisplayName("更换签名密钥后旧 token 立即失效")
    void shouldRejectTokenSignedByAnotherKey() {
        String token = jwtUtils.generateToken(sampleUser());

        JwtUtils another = new JwtUtils();
        ReflectionTestUtils.setField(another, "secret", "another-secret-key-at-least-32-bytes-long!!");
        ReflectionTestUtils.setField(another, "expire", 3_600_000L);
        ReflectionTestUtils.setField(another, "prefix", "Bearer ");
        ReflectionTestUtils.setField(another, "header", "Authorization");
        another.init();

        assertThat(another.parseToken(token)).isNull();
    }

    @Test
    @DisplayName("空串与乱码返回 null 而不是抛异常")
    void shouldReturnNullForGarbage() {
        assertThat(jwtUtils.parseToken(null)).isNull();
        assertThat(jwtUtils.parseToken("")).isNull();
        assertThat(jwtUtils.parseToken("not-a-jwt")).isNull();
    }

    @Test
    @DisplayName("从 Authorization 头解析 token，且拒绝不带 Bearer 前缀的值")
    void shouldResolveTokenFromHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer abc.def.ghi");
        assertThat(jwtUtils.resolveToken(request)).isEqualTo("abc.def.ghi");

        // 实现上严格要求前缀：避免把任意头部值当作凭据
        MockHttpServletRequest noPrefix = new MockHttpServletRequest();
        noPrefix.addHeader("Authorization", "abc.def.ghi");
        assertThat(jwtUtils.resolveToken(noPrefix)).isNull();

        MockHttpServletRequest empty = new MockHttpServletRequest();
        assertThat(jwtUtils.resolveToken(empty)).isNull();
    }

    @Test
    @DisplayName("token 有效期与配置一致")
    void shouldReportConfiguredExpire() {
        assertThat(jwtUtils.getExpire()).isEqualTo(3_600_000L);

        String token = jwtUtils.generateToken(sampleUser());
        assertThat(jwtUtils.getExpiration(token)).isNotNull();
    }
}
