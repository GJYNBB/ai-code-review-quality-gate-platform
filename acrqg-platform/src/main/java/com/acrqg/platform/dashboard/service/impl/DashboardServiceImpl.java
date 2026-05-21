package com.acrqg.platform.dashboard.service.impl;

import com.acrqg.platform.audit.event.AuditEvent;
import com.acrqg.platform.common.api.ErrorCode;
import com.acrqg.platform.common.exception.BusinessException;
import com.acrqg.platform.dashboard.dto.DashboardQuery;
import com.acrqg.platform.dashboard.dto.QualityTrendDTO;
import com.acrqg.platform.dashboard.dto.RiskFileDTO;
import com.acrqg.platform.dashboard.dto.TrendPointDTO;
import com.acrqg.platform.dashboard.repository.DashboardMapper;
import com.acrqg.platform.dashboard.service.DashboardService;
import com.acrqg.platform.infra.security.AuthenticatedUser;
import com.acrqg.platform.infra.security.CurrentUserHolder;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * {@link DashboardService} 默认实现（B4-C.2）。
 *
 * <p>核心职责：
 * <ol>
 *   <li>调用 {@link DashboardMapper} 完成 SQL 聚合；</li>
 *   <li>把 SQL 结果映射为 {@link TrendPointDTO} / {@link RiskFileDTO}；</li>
 *   <li>对 trend 在 Service 层补 0：缺失日期补 {@code taskCount=0, passRate=null, avgScore=null}；</li>
 *   <li>使用 Caffeine 缓存（TTL=60s，maxSize=1024）减少 DB 压力；</li>
 *   <li>通过 {@link ApplicationEventPublisher} 发布 {@code DASHBOARD_QUERIED}
 *       审计事件（R22 系列覆盖：操作记录与可追溯）。</li>
 * </ol>
 *
 * <p><b>时区策略</b>：
 * <ul>
 *   <li>{@code DashboardQuery.startDate / endDate} 是日历日期，按 UTC 解释；</li>
 *   <li>SQL 端把 {@code review_task.created_at} 转 {@code AT TIME ZONE 'UTC'}::date 进行 GROUP BY；</li>
 *   <li>Service 层把 {@code [startDate, endDate]} 闭区间转换为
 *       {@code [startDate UTC 00:00, (endDate+1) UTC 00:00)} 的半开区间，
 *       减少 SQL 端的 INDEX 失效风险（避免对 {@code created_at} 列再做函数调用）。</li>
 * </ul>
 *
 * <p><b>缓存策略</b>：
 * <ul>
 *   <li>trend 缓存 key：{@code "trend:{projectId}:{md5(query)}"}；</li>
 *   <li>riskFiles 缓存 key：{@code "risk:{projectId}:{limit}"}；</li>
 *   <li>缓存项不区分调用者——所有项目成员共享同一缓存项，进一步降低延迟；
 *       权限校验在 Controller 边界已经完成（R18.4），缓存命中后再次发布审计事件
 *       仍然能保留个人操作痕迹。</li>
 * </ul>
 *
 * <p>Covers: R18.1, R18.2, R18.3, R18.4, R18.5, R22.1。
 */
@Service
public class DashboardServiceImpl implements DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardServiceImpl.class);

    /** 风险文件 TopN 的硬约束上限。 */
    static final int RISK_FILE_LIMIT_MAX = 100;

    /** 趋势聚合 BigDecimal 字段的固定保留小数位。 */
    private static final int DECIMAL_SCALE = 4;

    /** 缓存 TTL；与 design 中 60s 描述一致。 */
    private static final long CACHE_TTL_SECONDS = 60L;

    /** 缓存上限：trend + riskFiles 共用，防止内存膨胀。 */
    private static final long CACHE_MAX_SIZE = 1024L;

    private final DashboardMapper dashboardMapper;
    private final ApplicationEventPublisher eventPublisher;

    /** 跨方法共享的简单 Caffeine 缓存。 */
    private final Cache<String, Object> cache;

    public DashboardServiceImpl(DashboardMapper dashboardMapper,
                                 ApplicationEventPublisher eventPublisher) {
        this.dashboardMapper = dashboardMapper;
        this.eventPublisher = eventPublisher;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(java.time.Duration.ofSeconds(CACHE_TTL_SECONDS))
                .maximumSize(CACHE_MAX_SIZE)
                .build();
    }

    // ---------------------------------------------------------------------
    // trend
    // ---------------------------------------------------------------------

    @Override
    public QualityTrendDTO trend(Long projectId, DashboardQuery query) {
        if (projectId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "projectId 不能为空");
        }
        if (query == null || query.startDate() == null || query.endDate() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "startDate/endDate 不能为空");
        }
        // 防御性：即使 Bean Validation 已过滤，这里再校验一次（保护程序化调用路径）
        if (query.endDate().isBefore(query.startDate())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "endDate 不能早于 startDate");
        }
        long span = ChronoUnit.DAYS.between(query.startDate(), query.endDate());
        if (span > DashboardQuery.MAX_RANGE_DAYS) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "time range cannot exceed 365 days");
        }

        String cacheKey = "trend:" + projectId + ":" + queryFingerprint(query);
        QualityTrendDTO cached = (QualityTrendDTO) cache.getIfPresent(cacheKey);
        QualityTrendDTO result;
        boolean cacheHit;
        if (cached != null) {
            result = cached;
            cacheHit = true;
        } else {
            result = computeTrend(projectId, query);
            cache.put(cacheKey, result);
            cacheHit = false;
        }

        publishAudit("DASHBOARD_QUERIED", projectId, detailOf(
                "type", "trend",
                "startDate", query.startDate().toString(),
                "endDate", query.endDate().toString(),
                "branch", query.branch(),
                "cacheHit", cacheHit,
                "points", result.points().size()));

        return result;
    }

    private QualityTrendDTO computeTrend(Long projectId, DashboardQuery query) {
        OffsetDateTime startInclusive = query.startDate().atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime endExclusive = query.endDate().plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);

        List<Map<String, Object>> rows = dashboardMapper.aggregateTrend(
                projectId, startInclusive, endExclusive,
                isBlank(query.branch()) ? null : query.branch());

        // 把数据库行按 LocalDate 索引
        Map<LocalDate, Map<String, Object>> byDay = new HashMap<>(rows.size() * 2);
        for (Map<String, Object> row : rows) {
            LocalDate day = toLocalDate(row.get("day"));
            if (day != null) {
                byDay.put(day, row);
            }
        }

        // 在 Service 层补齐缺失日，构造完整时间序列
        long days = ChronoUnit.DAYS.between(query.startDate(), query.endDate()) + 1L;
        List<TrendPointDTO> points = new ArrayList<>((int) days);

        long aggTaskCount = 0L;
        long aggPassCount = 0L;
        BigDecimal aggScoreSum = BigDecimal.ZERO;
        long aggScoreCount = 0L;

        for (long offset = 0; offset < days; offset++) {
            LocalDate day = query.startDate().plusDays(offset);
            Map<String, Object> row = byDay.get(day);
            if (row == null) {
                points.add(new TrendPointDTO(day, 0L, 0L, 0L, null, null, null));
                continue;
            }
            long taskCount = toLong(row.get("task_count"));
            long passCount = toLong(row.get("pass_count"));
            long failCount = toLong(row.get("fail_count"));
            BigDecimal avgScore = toBigDecimalScaled(row.get("avg_score"));
            BigDecimal avgDuration = toBigDecimalScaled(row.get("avg_duration_seconds"));
            BigDecimal passRate = computeRate(passCount, taskCount);

            points.add(new TrendPointDTO(day, taskCount, passCount, failCount,
                    passRate, avgScore, avgDuration));

            aggTaskCount += taskCount;
            aggPassCount += passCount;
            if (avgScore != null) {
                // 区间总平均：用 SUM(score) 更精确，但聚合 SQL 只返回 AVG；
                // 这里以 "AVG(day) * taskCount" 还原 SUM，再除以总 taskCount。
                aggScoreSum = aggScoreSum.add(avgScore.multiply(BigDecimal.valueOf(taskCount)));
                aggScoreCount += taskCount;
            }
        }

        BigDecimal overallPassRate = computeRate(aggPassCount, aggTaskCount);
        BigDecimal overallAvgScore = aggScoreCount == 0L ? null
                : aggScoreSum.divide(BigDecimal.valueOf(aggScoreCount), DECIMAL_SCALE, RoundingMode.HALF_UP);

        QualityTrendDTO.Totals totals = new QualityTrendDTO.Totals(
                aggTaskCount, overallPassRate, overallAvgScore);

        return new QualityTrendDTO(projectId, query.startDate(), query.endDate(),
                Collections.unmodifiableList(points), totals);
    }

    // ---------------------------------------------------------------------
    // topRiskFiles
    // ---------------------------------------------------------------------

    @Override
    public List<RiskFileDTO> topRiskFiles(Long projectId, int limit) {
        if (projectId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "projectId 不能为空");
        }
        int safeLimit = limit;
        if (safeLimit <= 0) {
            safeLimit = 10;
        }
        if (safeLimit > RISK_FILE_LIMIT_MAX) {
            safeLimit = RISK_FILE_LIMIT_MAX;
        }

        String cacheKey = "risk:" + projectId + ":" + safeLimit;
        @SuppressWarnings("unchecked")
        List<RiskFileDTO> cached = (List<RiskFileDTO>) cache.getIfPresent(cacheKey);
        List<RiskFileDTO> result;
        boolean cacheHit;
        if (cached != null) {
            result = cached;
            cacheHit = true;
        } else {
            List<Map<String, Object>> rows = dashboardMapper.topRiskFiles(projectId, safeLimit);
            List<RiskFileDTO> mapped = new ArrayList<>(rows.size());
            for (Map<String, Object> row : rows) {
                String filePath = Objects.toString(row.get("file_path"), "");
                long issueCount = toLong(row.get("issue_count"));
                BigDecimal weighted = toBigDecimalRaw(row.get("weighted_score"));
                long criticalCount = toLong(row.get("critical_count"));
                long highCount = toLong(row.get("high_count"));
                mapped.add(new RiskFileDTO(filePath, issueCount, weighted, criticalCount, highCount));
            }
            result = Collections.unmodifiableList(mapped);
            cache.put(cacheKey, result);
            cacheHit = false;
        }

        publishAudit("DASHBOARD_QUERIED", projectId, detailOf(
                "type", "topRiskFiles",
                "limit", safeLimit,
                "cacheHit", cacheHit,
                "files", result.size()));

        return result;
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    private void publishAudit(String action, Long projectId, Map<String, Object> detail) {
        Optional<AuthenticatedUser> caller = CurrentUserHolder.optional();
        Long opId = caller.map(AuthenticatedUser::id).orElse(null);
        String opUsername = caller.map(AuthenticatedUser::username).orElse("SYSTEM");
        try {
            AuditEvent event = AuditEvent.of(
                    opId, opUsername, action, "PROJECT",
                    projectId == null ? null : projectId.toString(),
                    null, detail);
            eventPublisher.publishEvent(event);
        } catch (RuntimeException ex) {
            // 审计失败不应影响主流程
            log.warn("publish DASHBOARD_QUERIED audit failed: projectId={} err={}",
                    projectId, ex.toString());
        }
    }

    /** 构造保留插入顺序的 detail Map。可变长键值对参数；{@code null} 值会被保留。 */
    private static Map<String, Object> detailOf(Object... kv) {
        if (kv == null || kv.length == 0) {
            return Collections.emptyMap();
        }
        if ((kv.length & 1) != 0) {
            throw new IllegalArgumentException("detailOf requires even number of args");
        }
        Map<String, Object> map = new LinkedHashMap<>(kv.length / 2);
        for (int i = 0; i < kv.length; i += 2) {
            map.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return map;
    }

    /**
     * 生成查询参数指纹，用于缓存 key。
     *
     * <p>使用 MD5（16 字节十六进制）足以避免冲突；仅作为缓存路由用，无安全敏感性。
     * 当 MD5 不可用（极少见）退化为字符串拼接 hashCode 的十六进制。
     */
    private static String queryFingerprint(DashboardQuery query) {
        String raw = query.startDate() + "|" + query.endDate() + "|"
                + (query.branch() == null ? "" : query.branch());
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            return Integer.toHexString(raw.hashCode());
        }
    }

    /** 计算通过率；分母为 0 时返回 {@code null}。 */
    private static BigDecimal computeRate(long pass, long total) {
        if (total <= 0L) {
            return null;
        }
        return BigDecimal.valueOf(pass)
                .divide(BigDecimal.valueOf(total), DECIMAL_SCALE, RoundingMode.HALF_UP);
    }

    private static long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    /** 把 SQL 返回值转 {@link BigDecimal} 并固定 4 位小数；{@code null} 透传。 */
    private static BigDecimal toBigDecimalScaled(Object value) {
        BigDecimal raw = toBigDecimalRaw(value);
        return raw == null ? null : raw.setScale(DECIMAL_SCALE, RoundingMode.HALF_UP);
    }

    /** 把 SQL 返回值转 {@link BigDecimal}（不重定标）；{@code null} 透传。 */
    private static BigDecimal toBigDecimalRaw(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static LocalDate toLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate ld) {
            return ld;
        }
        if (value instanceof Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        if (value instanceof java.util.Date d) {
            return d.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
        }
        try {
            return LocalDate.parse(value.toString());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
