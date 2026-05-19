package com.acrqg.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * AI 辅助代码评审与质量门禁平台 - 后端应用启动类。
 *
 * <p>同一 jar 通过 Spring Profile 组合切换 6 种运行身份：
 * <ul>
 *   <li>{@code dev,api}   / {@code dev,worker}   - 本地开发</li>
 *   <li>{@code test,api}  / {@code test,worker}  - 集成测试（Testcontainers）</li>
 *   <li>{@code prod,api}  / {@code prod,worker}  - 生产部署</li>
 * </ul>
 * 其中 env 维度（dev/test/prod）控制日志级别、Swagger UI、HTTPS 强制等环境差异；
 * role 维度（api/worker）控制 Web 控制器是否注册以及是否消费 Redis Stream。
 *
 * <p>开关说明：
 * <ul>
 *   <li>{@code @SpringBootApplication(scanBasePackages = "com.acrqg.platform")}：
 *       将组件扫描限定在平台基础包，避免误扫到第三方依赖中的 {@code @Component}。</li>
 *   <li>{@code @EnableScheduling}：开启 {@code @Scheduled} 支持，供 Worker 阻塞拉取
 *       Redis Stream、定时任务（任务超时巡检、参数热更新心跳）使用。</li>
 *   <li>{@code @EnableAspectJAutoProxy(proxyTargetClass = true)}：开启 AspectJ 自动代理，
 *       供权限切面 {@code PermissionAspect}、响应掩码切面 {@code ResponseMaskingAspect}
 *       等基础设施类切面工作。proxyTargetClass=true 强制使用 CGLIB 子类代理，避免
 *       仅有具体类（无接口）的 {@code @Service} 因 JDK 动态代理失败。</li>
 * </ul>
 *
 * <p>关联文档：design.md §3 (Tech Stack)、§4 (Backend Layout)、§17.3 (Profiles)。
 *
 * @see <a href="../../../../../../../.kiro/specs/ai-code-review-quality-gate-platform/requirements.md">
 *      Requirements R17.3, R24.6</a>
 */
@SpringBootApplication(scanBasePackages = "com.acrqg.platform")
@EnableScheduling
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AcrqgApplication {

    public static void main(String[] args) {
        SpringApplication.run(AcrqgApplication.class, args);
    }
}
