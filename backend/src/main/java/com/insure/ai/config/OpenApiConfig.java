package com.insure.ai.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI(Swagger)文档配置
 * <p>本项目存在两套完全独立的账号体系,文档按访问前缀拆成两个分组,避免混在一起:</p>
 * <ul>
 *     <li><b>管理端</b> {@code /admin/**} —— 业务操作人员,登录后从 {@code /admin/auth/login} 取 token</li>
 *     <li><b>客户端</b> {@code /auth/**}、{@code /chat/**}、{@code /file/**} —— 客户,H5 端使用</li>
 * </ul>
 * <p>两套 token 不能混用:管理端接口只认 {@code ROLE_ADMIN},客户端 token 访问一律 403。</p>
 * <p>访问入口(服务带 {@code context-path: /api}):</p>
 * <ul>
 *     <li>Swagger UI: {@code http://localhost:8080/api/swagger-ui.html}</li>
 *     <li>管理端 JSON: {@code http://localhost:8080/api/v3/api-docs/admin-api}</li>
 *     <li>客户端 JSON: {@code http://localhost:8080/api/v3/api-docs/client-api}</li>
 * </ul>
 *
 * @author insurance-ai
 */
@Configuration
public class OpenApiConfig {

    /** JWT 认证方案在文档中的名称,与 SecurityScheme 注册名保持一致 */
    public static final String SECURITY_SCHEME_NAME = "BearerAuth";

    /** 服务上下文路径,用于拼接文档中的 server 地址 */
    @Value("${server.servlet.context-path:}")
    private String contextPath;

    @Value("${spring.application.name:insurance-ai-service}")
    private String applicationName;

    /**
     * 全局文档信息 + JWT 认证方案
     * <p>配置后 Swagger UI 右上角会出现 Authorize 按钮,填入登录接口返回的 token 即可调试受保护接口。</p>
     */
    @Bean
    public OpenAPI insuranceOpenAPI() {
        Server server = new Server();
        server.setUrl(contextPath == null || contextPath.isBlank() ? "/" : contextPath);
        server.setDescription("当前服务(context-path 已包含)");

        return new OpenAPI()
                .info(new Info()
                        .title("保险智能客服系统 API")
                        .version("1.0.0")
                        .description("""
                                保险行业智能客服系统后端接口文档。

                                **两套账号体系**
                                - 管理端:`sys_user`(业务操作人员),接口前缀 `/admin/**`,角色 `SUPER_ADMIN` / `OPERATOR`
                                - 客户端:`cus_user`(咨询客户),接口前缀 `/auth/**`、`/chat/**`,登录账号为手机号

                                **调试步骤**
                                1. 调 `/admin/auth/login` 或 `/auth/login` 拿到 `data.token`
                                2. 点右上角 Authorize,填写 `token` 值(不需要手写 `Bearer ` 前缀)
                                3. 之后所有请求会自动带上 `Authorization: Bearer {token}`

                                演示账号:管理端 `admin/123456`、`operator/123456`;客户端 `13900000001/123456`
                                """)
                        .contact(new Contact().name("insurance-ai-service").url("http://localhost:5173")))
                .servers(List.of(server))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .in(SecurityScheme.In.HEADER)
                                .description("登录接口返回的 JWT,填写时不带 Bearer 前缀")))
                // 默认对所有接口生效,免登录接口(白名单)忽略该要求即可
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
    }

    /**
     * 管理端分组:{@code /admin/**}
     */
    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
                .group("admin-api")
                .displayName("管理端接口")
                .pathsToMatch("/admin/**")
                .build();
    }

    /**
     * 客户端分组:{@code /auth/**}、{@code /chat/**}、{@code /service/**}、{@code /file/**}
     * <p>新增客户端模块时要同步补到这里，否则接口不会出现在 Swagger 的客户端分组里</p>
     */
    @Bean
    public GroupedOpenApi clientApi() {
        return GroupedOpenApi.builder()
                .group("client-api")
                .displayName("客户端(手机 H5)接口")
                .pathsToMatch("/auth/**", "/chat/**", "/service/**", "/file/**")
                .build();
    }
}
