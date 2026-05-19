package com.acrqg.platform.infra.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * AI 评审服务健康指示器（占位）。
 *
 * <p>由 Spring Boot Actuator 自动发现并注册到 {@code /actuator/health}：
 * bean 名称去掉 {@code HealthIndicator} 后缀作为标识，本类暴露的指示器名称为
 * <b>{@code aiService}</b>。
 *
 * <p><b>当前状态</b>：在 B0-A 基础设施 bootstrap 阶段，AI 评审客户端（{@code ai} 包，
 * design §6.6）尚未实现。本指示器返回 {@link Health#unknown() UNKNOWN} 以满足
 * R24.6 对 {@code /actuator/health} 完整 indicator 列表的要求，避免 K8s readiness
 * 在 AI 模块缺位时把整个进程错误地判定为 DOWN。
 *
 * <p><b>后续</b>：B3-E.6 将本指示器替换为基于 {@code AiReviewClient} 的真实探活
 * （HEAD / 健康端点 + 超时窗口），并在降级开启时维持 UP（仅 detail 标注 degraded）。
 *
 * <p>聚合策略说明：Spring Boot 默认的 {@code HealthAggregator} 把 UNKNOWN
 * 视为不影响整体状态的中性值（仅 DOWN / OUT_OF_SERVICE 会拉低整体），因此本占位
 * 指示器在 AI 未配置时不会令 {@code /actuator/health} 整体变红。
 */
@Component
public class AiServiceHealthIndicator implements HealthIndicator {

    /** 占位说明文本；B3-E.6 替换实现时一并删除。 */
    static final String NOTE = "ai service health indicator pending B3-E.6";

    @Override
    public Health health() {
        return Health.unknown()
                .withDetail("note", NOTE)
                .build();
    }
}
