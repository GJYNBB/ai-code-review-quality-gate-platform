package com.acrqg.platform.infra.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * springdoc-openapi 全局配置。
 *
 * <p>声明：
 * <ul>
 *   <li>API 元信息：标题、版本、描述（与 02 号 RESTful 接口设计文档对齐）；</li>
 *   <li>统一安全方案 {@code bearer-auth}（HTTP Bearer，{@code bearerFormat=JWT}），
 *       并以全局 {@link SecurityRequirement} 应用到所有 operation；非鉴权接口
 *       （登录 / 刷新 / Webhook / health）由各 Controller 显式标注
 *       {@code @SecurityRequirements({})} 即可豁免（B1-A 起统一处理）。</li>
 * </ul>
 *
 * <p>swagger-ui 的暴露开关由 {@code springdoc.swagger-ui.enabled}
 * 与 {@code springdoc.api-docs.enabled} 控制（dev / test 默认开启，prod 默认关闭，
 * 详见 {@code application-prod.yml}）。
 *
 * <p>Covers: R23.1（受保护接口需 Bearer JWT），R25.2（OpenAPI 契约基线）。
 */
@Configuration
public class OpenApiConfig {

    /** 全局安全方案名；与 Controller 上的 {@code @SecurityRequirement(name=...)} 一致。 */
    public static final String SECURITY_SCHEME_NAME = "bearer-auth";

    @Bean
    public OpenAPI openAPI() {
        Info info = new Info()
                .title("AI辅助代码评审与质量门禁平台 API")
                .version("v1.0.0")
                .description("AI 辅助代码评审与质量门禁平台 RESTful 接口（Spring Boot 3 / Java 17）");

        SecurityScheme bearerScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("使用 /api/v1/auth/login 接口签发的 accessToken，置于 Authorization 头：Bearer <token>");

        Components components = new Components()
                .addSecuritySchemes(SECURITY_SCHEME_NAME, bearerScheme);

        return new OpenAPI()
                .info(info)
                .components(components)
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
    }
}
