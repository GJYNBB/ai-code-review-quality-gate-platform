package com.acrqg.platform.audit.service.impl;

import com.acrqg.platform.audit.config.AuditAsyncConfig;
import com.acrqg.platform.audit.domain.AuditLog;
import com.acrqg.platform.audit.dto.AuditLogDTO;
import com.acrqg.platform.audit.dto.AuditQuery;
import com.acrqg.platform.audit.event.AuditEvent;
import com.acrqg.platform.audit.repository.AuditLogMapper;
import com.acrqg.platform.audit.service.AuditService;
import com.acrqg.platform.common.api.PageResult;
import com.acrqg.platform.common.util.JsonUtils;
import com.acrqg.platform.common.util.MaskUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * {@link AuditService} 默认实现。
 *
 * <p>主要职责：
 * <ol>
 *   <li>{@link #record(AuditEvent)}：异步把审计事件落库。
 *       <ul>
 *         <li>用 {@link AuditAsyncConfig#EXECUTOR_BEAN_NAME} 指定的线程池
 *             （core=2 / max=4 / queue=1024 / prefix=audit-）异步执行；</li>
 *         <li>写入前调用 {@link MaskUtils#maskJsonNode(JsonNode)} 对 detail 中
 *             命中 {@link MaskUtils#SENSITIVE_KEYS} 的字段做掩码（R22.5 / R23.3）；</li>
 *         <li>序列化为 JSON 字符串后写入 {@code audit_log.detail}；
 *             {@link AuditLogMapper#insert(AuditLog)} 内部使用
 *             {@code CAST(... AS JSONB)} 把字符串转 JSONB。</li>
 *       </ul>
 *   </li>
 *   <li>{@link #page(AuditQuery)}：按 operator / action / 时间区间分页（R22.2）。
 *       使用两次 SQL 调用（{@code COUNT} + {@code LIMIT/OFFSET 列表}）而非数据库
 *       窗口函数：避免在分页插件外引入复杂的 over() 子句，便于 Mapper 简洁；
 *       两次查询都走同一个 {@code idx_audit_*} 索引，性能可接受。</li>
 * </ol>
 *
 * <p>关于 {@link AuditEvent} 的"权威实现"说明：本类与 audit 包内的
 * {@link AuditEvent} 紧耦合。当 B1-A 分支可能存在临时占位 AuditEvent 时，
 * 合并到 develop 后应统一使用本包内的版本（字段已对齐）。
 *
 * <p>Covers: R22.1, R22.2, R22.3, R22.5。
 */
@Service
public class AuditServiceImpl implements AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditServiceImpl.class);

    /** 用于把 {@code Map<String,Object>} 转成 Jackson 树以便统一掩码。 */
    private static final ObjectMapper MAPPER = JsonUtils.mapper();

    /** 反序列化时把 {@code detail} JSON 字符串转回 {@code Map<String,Object>}。 */
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final AuditLogMapper auditLogMapper;

    public AuditServiceImpl(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    // ---------------------------------------------------------------------
    // 写入
    // ---------------------------------------------------------------------

    @Async(AuditAsyncConfig.EXECUTOR_BEAN_NAME)
    @Override
    public void record(AuditEvent event) {
        if (event == null) {
            return;
        }
        try {
            AuditLog row = new AuditLog();
            row.setOperatorId(event.operatorId());
            row.setOperatorUsername(event.operatorUsername());
            row.setAction(event.action());
            row.setResourceType(event.resourceType());
            row.setResourceId(event.resourceId());
            row.setIp(event.ip());
            row.setDetailJson(maskAndSerialize(event.detail()));
            // createdAt 留 null，由 DB DEFAULT NOW() 兜底；
            // 如果未来希望保持事件触发时间，可改为 OffsetDateTime.now()。
            auditLogMapper.insert(row);
        } catch (Exception ex) {
            // 不向上抛出：审计写库失败不应影响业务主路径
            log.warn("failed to persist audit event action={} resource={}/{}",
                    event.action(), event.resourceType(), event.resourceId(), ex);
        }
    }

    // ---------------------------------------------------------------------
    // 查询
    // ---------------------------------------------------------------------

    @Override
    public PageResult<AuditLogDTO> page(AuditQuery query) {
        AuditQuery q = query == null
                ? new AuditQuery(null, null, null, null, 1, 20)
                : query;
        int page = q.safePage();
        int pageSize = q.safePageSize();
        int offset = (page - 1) * pageSize;

        long total = auditLogMapper.count(q.operator(), q.action(), q.startDate(), q.endDate());
        List<AuditLogDTO> items;
        if (total == 0) {
            items = Collections.emptyList();
        } else {
            List<AuditLog> rows = auditLogMapper.selectList(
                    q.operator(), q.action(), q.startDate(), q.endDate(),
                    pageSize, offset);
            items = new ArrayList<>(rows.size());
            for (AuditLog row : rows) {
                items.add(toDTO(row));
            }
        }
        return PageResult.of(items, page, pageSize, total);
    }

    // ---------------------------------------------------------------------
    // 内部工具
    // ---------------------------------------------------------------------

    /**
     * 对 detail 进行敏感字段掩码并序列化为 JSON 字符串。
     *
     * <p>步骤：
     * <ol>
     *   <li>把 {@code Map<String,Object>} 转换为 Jackson 树（{@link ObjectNode}）；</li>
     *   <li>调用 {@link MaskUtils#maskJsonNode} 原地把命中
     *       {@link MaskUtils#SENSITIVE_KEYS} 的字段值改为 {@code "****"}；</li>
     *   <li>{@link ObjectMapper#writeValueAsString} 输出 JSON 字符串。</li>
     * </ol>
     *
     * <p>{@code null} / 空 Map 直接返回 {@code "{}"}。
     */
    static String maskAndSerialize(Map<String, Object> detail) {
        if (detail == null || detail.isEmpty()) {
            return "{}";
        }
        try {
            JsonNode tree = MAPPER.valueToTree(detail);
            JsonNode masked = MaskUtils.maskJsonNode(tree);
            return MAPPER.writeValueAsString(masked);
        } catch (Exception ex) {
            // 兜底：序列化失败时仍保留 action 等顶层字段，detail 写入空对象
            log.warn("failed to serialize audit detail; fallback to '{}'", "{}", ex);
            return "{}";
        }
    }

    /** {@link AuditLog} → {@link AuditLogDTO}：仅做反序列化与时区转换。 */
    static AuditLogDTO toDTO(AuditLog row) {
        Map<String, Object> detail = parseDetail(row.getDetailJson());
        OffsetDateTime createdAt = row.getCreatedAt();
        // 兜底：当 createdAt 为 null 时（极少见，DB 触发器应已填充）使用 epoch。
        if (createdAt == null) {
            createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
        return new AuditLogDTO(
                row.getId(),
                row.getOperatorId(),
                row.getOperatorUsername(),
                row.getAction(),
                row.getResourceType(),
                row.getResourceId(),
                row.getIp(),
                detail,
                createdAt);
    }

    /** 把 detail JSON 字符串解析回 Map。容错：解析失败时返回空 Map。 */
    static Map<String, Object> parseDetail(String detailJson) {
        if (detailJson == null || detailJson.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return MAPPER.readValue(detailJson, MAP_TYPE);
        } catch (Exception ex) {
            log.warn("failed to parse audit detail json; returning empty map. raw={}", detailJson, ex);
            return Collections.emptyMap();
        }
    }
}
