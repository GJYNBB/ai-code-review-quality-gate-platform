package com.acrqg.platform.audit.service;

import com.acrqg.platform.audit.dto.AuditLogDTO;
import com.acrqg.platform.audit.dto.AuditQuery;
import com.acrqg.platform.audit.event.AuditEvent;
import com.acrqg.platform.common.api.PageResult;

/**
 * 审计日志服务。
 *
 * <p>提供两类能力：
 * <ol>
 *   <li>{@link #record(AuditEvent)} —— 异步落库审计事件；写入前会对
 *       {@link AuditEvent#detail()} 中的敏感字段（password / accessToken / apiKey /
 *       webhookSecret 等）进行掩码，对应 R22.5 / R23.3。</li>
 *   <li>{@link #page(AuditQuery)} —— 按操作者、动作名、时间区间分页查询审计日志，
 *       供 SYSTEM_ADMIN 通过 {@code GET /api/v1/admin/audit-logs} 检索（R22.2）。</li>
 * </ol>
 *
 * <p>实现细节：
 * <ul>
 *   <li>{@link #record(AuditEvent)} 标记为 {@code @Async("auditTaskExecutor")}，
 *       由 {@link com.acrqg.platform.audit.config.AuditAsyncConfig} 配置专用线程池
 *       （core=2 / max=4 / queue=1024）。这避免审计写库失败 / 慢操作影响业务主路径。</li>
 *   <li>本服务<b>不</b>提供 update / delete 接口；底层 {@code AuditLogMapper}
 *       同样不暴露这些方法。审计日志的 append-only 由"代码层 + 数据库触发器"双重保障
 *       （R22.4）。</li>
 * </ul>
 *
 * <p>Covers: R22.1, R22.2, R22.3, R22.5。
 */
public interface AuditService {

    /**
     * 异步记录一条审计事件。
     *
     * <p>内部流程：
     * <ol>
     *   <li>把 {@link AuditEvent#detail()} 转为 Jackson 树并调用
     *       {@link com.acrqg.platform.common.util.MaskUtils#maskJsonNode}
     *       对敏感键值原地掩码；</li>
     *   <li>序列化为 JSON 字符串作为 {@code audit_log.detail} 列值；</li>
     *   <li>调用 {@code AuditLogMapper.insert}。</li>
     * </ol>
     *
     * <p>本方法的异常会被 {@code AuditAsyncConfig.AsyncExceptionHandler} 捕获并
     * 仅记录日志，不会向上抛出，避免影响业务主线程。
     *
     * @param event 审计事件，{@code null} 时直接忽略
     */
    void record(AuditEvent event);

    /**
     * 按条件分页查询审计日志。
     *
     * <p>仅 SYSTEM_ADMIN 可调用（由控制器层的 {@code @RequirePermission(role=SYSTEM_ADMIN)} 保证）。
     *
     * @param query 分页查询条件
     * @return 分页结果，{@code data.detail} 已是脱敏后的 JSON
     */
    PageResult<AuditLogDTO> page(AuditQuery query);
}
