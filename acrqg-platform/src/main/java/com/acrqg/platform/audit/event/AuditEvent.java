package com.acrqg.platform.audit.event;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 审计事件，由各业务服务通过 {@link org.springframework.context.ApplicationEventPublisher}
 * 发布，{@link AuditEventListener} 异步落库。
 *
 * <p>设计原则：
 * <ol>
 *   <li><b>领域解耦</b>：业务模块（auth / project / repository / admin / gate ...）
 *       不直接依赖 {@code AuditService}，而是发布 {@link AuditEvent}；M01 审计模块作为
 *       订阅方负责落库。这样能保证业务流程与审计写库的失败相互隔离（R22.1）。</li>
 *   <li><b>不可变载荷</b>：使用 Java {@code record} 表达不可变快照。{@link #detail}
 *       字段在构造时拷贝为不可修改 {@link Map}，避免发布方在事件被消费前修改原 Map
 *       而引发竞态。</li>
 *   <li><b>跨线程友好</b>：因 {@link AuditEventListener} 使用 {@code @Async}
 *       异步消费，事件载荷必须线程安全；不可变 + 拷贝即满足。</li>
 *   <li><b>权威实现</b>：本 record 是 audit 包的"权威 AuditEvent"。在 B1-A 分支
 *       中可能存在临时占位定义；当两个分支合并到 develop 时，B1-A 的占位需要替换为
 *       本类的引用，字段与构造方式已与 B1-A 保持兼容（同名字段、同顺序参数）。</li>
 * </ol>
 *
 * <p>对应 requirements.md R22.1 / R22.3 / R22.5：
 * <ul>
 *   <li>R22.1 —— 关键操作必须记录审计；事件是触发记录的标准方式。</li>
 *   <li>R22.3 —— operatorId / operatorUsername / action / resourceType /
 *       resourceId / ip / detail 字段一一对应。</li>
 *   <li>R22.5 —— {@link #detail} 中可能包含敏感字段，由订阅方在落库前
 *       通过 {@code MaskUtils} 完成掩码。发布方<b>无需</b>预先掩码，避免业务侧
 *       重复实现。</li>
 * </ul>
 *
 * @param operatorId       操作者用户主键；{@code null} 表示系统级操作（如 PLATFORM_INIT）
 * @param operatorUsername 操作者用户名快照；可为 {@code "SYSTEM"} 等占位
 * @param action           动作名（建议使用大写蛇形：{@code LOGIN_SUCCESS} / {@code PROJECT_CREATED}）
 * @param resourceType     资源类型（{@code USER} / {@code PROJECT} / {@code REPOSITORY} / ...）
 * @param resourceId       资源主键的字符串形式；可为 {@code null}
 * @param ip               客户端 IP；可为 {@code null}（系统内部触发时常为空）
 * @param detail           操作明细，键值对，<b>可包含未掩码的敏感字段</b>
 */
public record AuditEvent(
        Long operatorId,
        String operatorUsername,
        String action,
        String resourceType,
        String resourceId,
        String ip,
        Map<String, Object> detail) {

    /**
     * 构造函数：对 {@link #detail} 做防御性拷贝并包装为不可修改 Map。
     * {@code null} 入参视为空 Map（{@link Collections#emptyMap()}）。
     */
    public AuditEvent {
        if (detail == null || detail.isEmpty()) {
            detail = Collections.emptyMap();
        } else {
            // 使用 LinkedHashMap 保留插入顺序，便于审计日志保持可读性
            detail = Collections.unmodifiableMap(new LinkedHashMap<>(detail));
        }
    }

    /**
     * 便捷构造：仅有 action（系统级操作，operatorId/username 由订阅方决定）。
     */
    public static AuditEvent system(String action, String resourceType, String resourceId,
                                    Map<String, Object> detail) {
        return new AuditEvent(null, "SYSTEM", action, resourceType, resourceId, null, detail);
    }

    /**
     * 便捷构造：携带操作者信息。
     */
    public static AuditEvent of(Long operatorId, String operatorUsername, String action,
                                 String resourceType, String resourceId, String ip,
                                 Map<String, Object> detail) {
        return new AuditEvent(operatorId, operatorUsername, action, resourceType, resourceId, ip, detail);
    }
}
