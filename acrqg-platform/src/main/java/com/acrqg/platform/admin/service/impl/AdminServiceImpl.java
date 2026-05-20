package com.acrqg.platform.admin.service.impl;

import com.acrqg.platform.admin.domain.ModelConfig;
import com.acrqg.platform.admin.domain.ScannerConfig;
import com.acrqg.platform.admin.domain.SystemParam;
import com.acrqg.platform.admin.dto.ModelConfigCreateRequest;
import com.acrqg.platform.admin.dto.ModelConfigDTO;
import com.acrqg.platform.admin.dto.ModelConfigUpdateRequest;
import com.acrqg.platform.admin.dto.ScannerConfigDTO;
import com.acrqg.platform.admin.dto.ScannerConfigRequest;
import com.acrqg.platform.admin.dto.SystemParamDTO;
import com.acrqg.platform.admin.repository.ModelConfigMapper;
import com.acrqg.platform.admin.repository.ScannerConfigMapper;
import com.acrqg.platform.admin.repository.SystemParamMapper;
import com.acrqg.platform.admin.service.AdminService;
import com.acrqg.platform.audit.event.AuditEvent;
import com.acrqg.platform.common.api.ErrorCode;
import com.acrqg.platform.common.exception.BusinessException;
import com.acrqg.platform.common.util.MaskUtils;
import com.acrqg.platform.infra.crypto.TokenEncryptor;
import com.acrqg.platform.infra.security.AuthenticatedUser;
import com.acrqg.platform.infra.security.CurrentUserHolder;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link AdminService} 的默认实现。
 *
 * <p>本类聚合三个子域的实现，文件按"模型 / 扫描器 / 系统参数"三段组织，
 * 避免每个子域单独建 Service 类带来的过度拆分（M10 整体复杂度有限）。
 *
 * <h3>关键策略</h3>
 * <ol>
 *   <li><b>加密入口唯一</b>：所有 apiKey / sensitive 系统参数明文均经
 *       {@link TokenEncryptor#encrypt} 后再落库，业务代码不直接接触
 *       {@link com.acrqg.platform.infra.crypto.AesGcmCipher AesGcmCipher}（R23.2）。</li>
 *   <li><b>响应永远脱敏</b>：所有 DTO 转换路径均使用 {@code "****"} 占位
 *       apiKey；敏感参数的 {@code paramValue} 同样替换。即便控制器忘记加
 *       {@code @ResponseMaskingAspect}，本类也已保证不会泄漏（R23.3 双重防御）。</li>
 *   <li><b>审计事件</b>：通过 {@link ApplicationEventPublisher#publishEvent} 发布
 *       {@link AuditEvent}，由 B1-B 的 {@code AuditEventListener} 异步落库。
 *       detail 中明确放置脱敏值，发布方不依赖订阅方的二次掩码。</li>
 *   <li><b>Redis pub/sub</b>：系统参数更新成功后，通过
 *       {@link StringRedisTemplate#convertAndSend} 在通道
 *       {@code param-changed:{key}} 上发布消息（R24.3 60s 热更新窗口）。
 *       消息体本身不携带敏感值，敏感参数发布字面量 {@code "<changed>"}。</li>
 * </ol>
 *
 * <p>Covers: R21, R22.1, R23.2, R23.3, R24.3。
 */
@Service
public class AdminServiceImpl implements AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminServiceImpl.class);

    /** 审计资源类型字面量。 */
    private static final String RESOURCE_MODEL_CONFIG = "MODEL_CONFIG";
    private static final String RESOURCE_SCANNER_CONFIG = "SCANNER_CONFIG";
    private static final String RESOURCE_SYSTEM_PARAM = "SYSTEM_PARAM";

    /** 审计 action 字面量。 */
    private static final String ACTION_MODEL_CONFIG_CREATED = "MODEL_CONFIG_CREATED";
    private static final String ACTION_MODEL_CONFIG_UPDATED = "MODEL_CONFIG_UPDATED";
    private static final String ACTION_SCANNER_CONFIG_UPSERTED = "SCANNER_CONFIG_UPSERTED";
    private static final String ACTION_SYSTEM_PARAM_UPDATED = "SYSTEM_PARAM_UPDATED";

    /** Redis pub/sub 通道前缀（design.md §4.4 / R24.3）。 */
    static final String CHANNEL_PARAM_CHANGED_PREFIX = "param-changed:";

    /** 敏感参数在 Redis 通道中发布的占位值，避免明文穿透到所有订阅者。 */
    static final String CHANNEL_PAYLOAD_SENSITIVE_CHANGED = "<changed>";

    /** 已知系统参数的类型校验范围。 */
    private static final Map<String, IntRange> KNOWN_PARAM_RANGES;

    static {
        Map<String, IntRange> m = new LinkedHashMap<>();
        m.put("review.worker.concurrency", new IntRange(1, 32));
        m.put("ai.review.timeout.seconds", new IntRange(10, 300));
        m.put("diff.maxLinesPerFile", new IntRange(100, 50_000));
        KNOWN_PARAM_RANGES = Collections.unmodifiableMap(m);
    }

    private final ModelConfigMapper modelConfigMapper;
    private final ScannerConfigMapper scannerConfigMapper;
    private final SystemParamMapper systemParamMapper;
    private final TokenEncryptor tokenEncryptor;
    private final ApplicationEventPublisher eventPublisher;
    private final StringRedisTemplate stringRedisTemplate;

    public AdminServiceImpl(ModelConfigMapper modelConfigMapper,
                             ScannerConfigMapper scannerConfigMapper,
                             SystemParamMapper systemParamMapper,
                             TokenEncryptor tokenEncryptor,
                             ApplicationEventPublisher eventPublisher,
                             StringRedisTemplate stringRedisTemplate) {
        this.modelConfigMapper = modelConfigMapper;
        this.scannerConfigMapper = scannerConfigMapper;
        this.systemParamMapper = systemParamMapper;
        this.tokenEncryptor = tokenEncryptor;
        this.eventPublisher = eventPublisher;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    // =====================================================================
    // 模型管理（B1-D.3）
    // =====================================================================

    @Override
    @Transactional
    public ModelConfigDTO createModel(ModelConfigCreateRequest request) {
        AuthenticatedUser caller = CurrentUserHolder.requireCurrent();

        ModelConfig entity = new ModelConfig();
        entity.setName(request.name());
        entity.setBaseUrl(request.baseUrl());
        entity.setApiKeyEncrypted(tokenEncryptor.encrypt(request.apiKey()));
        entity.setTimeoutSeconds(request.timeoutSeconds());
        entity.setEnabled(Boolean.TRUE);

        try {
            modelConfigMapper.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "模型名称已存在: " + request.name(), ex);
        }

        publishAudit(caller, ACTION_MODEL_CONFIG_CREATED, RESOURCE_MODEL_CONFIG,
                String.valueOf(entity.getId()),
                detailOf(
                        "modelId", entity.getId(),
                        "name", entity.getName(),
                        "baseUrl", entity.getBaseUrl(),
                        "timeoutSeconds", entity.getTimeoutSeconds(),
                        "apiKey", MaskUtils.FULL_MASK));

        return toDTO(entity);
    }

    @Override
    public List<ModelConfigDTO> listModels() {
        List<ModelConfig> rows = modelConfigMapper.selectList(
                new QueryWrapper<ModelConfig>().orderByAsc("id"));
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<ModelConfigDTO> result = new ArrayList<>(rows.size());
        for (ModelConfig row : rows) {
            result.add(toDTO(row));
        }
        return result;
    }

    @Override
    public ModelConfigDTO getModel(Long id) {
        return toDTO(requireModelById(id));
    }

    @Override
    @Transactional
    public ModelConfigDTO updateModel(Long id, ModelConfigUpdateRequest request) {
        AuthenticatedUser caller = CurrentUserHolder.requireCurrent();

        ModelConfig existing = requireModelById(id);
        Map<String, Object> diff = new LinkedHashMap<>();
        boolean changed = false;

        if (request.baseUrl() != null && !request.baseUrl().equals(existing.getBaseUrl())) {
            diff.put("baseUrl", existing.getBaseUrl() + " -> " + request.baseUrl());
            existing.setBaseUrl(request.baseUrl());
            changed = true;
        }
        if (request.apiKey() != null && !request.apiKey().isEmpty()) {
            // 即便明文字符串相同，由于 IV 随机，密文必然不同；这里仅记录"已替换"事实
            existing.setApiKeyEncrypted(tokenEncryptor.encrypt(request.apiKey()));
            diff.put("apiKey", MaskUtils.FULL_MASK + " -> " + MaskUtils.FULL_MASK);
            changed = true;
        }
        if (request.timeoutSeconds() != null
                && !request.timeoutSeconds().equals(existing.getTimeoutSeconds())) {
            diff.put("timeoutSeconds",
                    existing.getTimeoutSeconds() + " -> " + request.timeoutSeconds());
            existing.setTimeoutSeconds(request.timeoutSeconds());
            changed = true;
        }
        if (request.enabled() != null && !request.enabled().equals(existing.getEnabled())) {
            diff.put("enabled", existing.getEnabled() + " -> " + request.enabled());
            existing.setEnabled(request.enabled());
            changed = true;
        }

        if (changed) {
            modelConfigMapper.updateById(existing);
            publishAudit(caller, ACTION_MODEL_CONFIG_UPDATED, RESOURCE_MODEL_CONFIG,
                    String.valueOf(id),
                    detailOf("modelId", id, "name", existing.getName(), "diff", diff));
        }

        return toDTO(existing);
    }

    @Override
    @Transactional
    public ModelConfigDTO enableDisableModel(Long id, boolean enabled) {
        AuthenticatedUser caller = CurrentUserHolder.requireCurrent();

        ModelConfig existing = requireModelById(id);
        if (existing.getEnabled() != null && existing.getEnabled() == enabled) {
            // no-op：状态未变化时仍返回 DTO，但不写审计、不写库
            return toDTO(existing);
        }
        boolean before = Boolean.TRUE.equals(existing.getEnabled());
        existing.setEnabled(enabled);
        modelConfigMapper.updateById(existing);

        publishAudit(caller, ACTION_MODEL_CONFIG_UPDATED, RESOURCE_MODEL_CONFIG,
                String.valueOf(id),
                detailOf("modelId", id,
                        "name", existing.getName(),
                        "diff", Map.of("enabled", before + " -> " + enabled)));

        return toDTO(existing);
    }

    @Override
    public String decryptModelApiKey(Long modelId) {
        // 内部接口：解密返回 apiKey 明文，仅供 B3-E AI 客户端瞬时使用。
        // 不写审计：调用频率高，且解密本身不构成"管理操作"，由 AI 调用链自行追踪。
        ModelConfig row = requireModelById(modelId);
        try {
            return tokenEncryptor.decrypt(row.getApiKeyEncrypted());
        } catch (RuntimeException ex) {
            // 密文格式破坏 / 密钥轮换未迁移：转为可识别的业务异常，避免泄漏底层栈
            log.error("decryptModelApiKey failed: modelId={} err={}", modelId, ex.toString());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "模型 apiKey 解密失败，请联系系统管理员", ex);
        }
    }

    private ModelConfig requireModelById(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模型 id 不能为空");
        }
        ModelConfig existing = modelConfigMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模型不存在: " + id);
        }
        return existing;
    }

    private static ModelConfigDTO toDTO(ModelConfig entity) {
        return new ModelConfigDTO(
                entity.getId(),
                entity.getName(),
                entity.getBaseUrl(),
                MaskUtils.FULL_MASK,
                entity.getTimeoutSeconds() == null ? 0 : entity.getTimeoutSeconds(),
                Boolean.TRUE.equals(entity.getEnabled()),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    // =====================================================================
    // 扫描器管理（B1-D.4）
    // =====================================================================

    @Override
    @Transactional
    public ScannerConfigDTO upsertScanner(ScannerConfigRequest request) {
        AuthenticatedUser caller = CurrentUserHolder.requireCurrent();

        ScannerConfig existing = scannerConfigMapper.selectByName(request.name());
        boolean inserting = (existing == null);
        ScannerConfig entity = inserting ? new ScannerConfig() : existing;

        Map<String, Object> diff = new LinkedHashMap<>();
        boolean enabledValue = request.enabled() == null ? Boolean.TRUE : request.enabled();

        if (inserting) {
            entity.setName(request.name());
            entity.setLanguage(request.language());
            entity.setEnabled(enabledValue);
            entity.setCommand(request.command());
            entity.setResultParserType(request.resultParserType());
            try {
                scannerConfigMapper.insert(entity);
            } catch (DuplicateKeyException ex) {
                // 极端并发：另一线程在 selectByName 与 insert 之间插了同名行；改走 update 路径
                ScannerConfig race = scannerConfigMapper.selectByName(request.name());
                if (race == null) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                            "扫描器名称已存在: " + request.name(), ex);
                }
                entity = race;
                inserting = false;
                applyScannerDiff(entity, request, enabledValue, diff);
                if (!diff.isEmpty()) {
                    scannerConfigMapper.updateById(entity);
                }
            }
        } else {
            applyScannerDiff(entity, request, enabledValue, diff);
            if (!diff.isEmpty()) {
                scannerConfigMapper.updateById(entity);
            }
        }

        Map<String, Object> auditDetail = new LinkedHashMap<>();
        auditDetail.put("scannerId", entity.getId());
        auditDetail.put("name", entity.getName());
        auditDetail.put("operation", inserting ? "INSERT" : "UPDATE");
        if (!diff.isEmpty()) {
            auditDetail.put("diff", diff);
        }
        publishAudit(caller, ACTION_SCANNER_CONFIG_UPSERTED, RESOURCE_SCANNER_CONFIG,
                String.valueOf(entity.getId()), auditDetail);

        return toDTO(entity);
    }

    @Override
    public List<ScannerConfigDTO> listScanners() {
        List<ScannerConfig> rows = scannerConfigMapper.selectList(
                new QueryWrapper<ScannerConfig>().orderByAsc("id"));
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<ScannerConfigDTO> result = new ArrayList<>(rows.size());
        for (ScannerConfig row : rows) {
            result.add(toDTO(row));
        }
        return result;
    }

    @Override
    public ScannerConfigDTO getScanner(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "扫描器 id 不能为空");
        }
        ScannerConfig row = scannerConfigMapper.selectById(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "扫描器不存在: " + id);
        }
        return toDTO(row);
    }

    /** 把 {@link ScannerConfigRequest} 中变化的字段写回 entity 并记录 diff。 */
    private static void applyScannerDiff(ScannerConfig entity, ScannerConfigRequest request,
                                         boolean enabledValue, Map<String, Object> diff) {
        if (!request.language().equals(entity.getLanguage())) {
            diff.put("language", entity.getLanguage() + " -> " + request.language());
            entity.setLanguage(request.language());
        }
        if (entity.getEnabled() == null || entity.getEnabled() != enabledValue) {
            diff.put("enabled", entity.getEnabled() + " -> " + enabledValue);
            entity.setEnabled(enabledValue);
        }
        if (!request.command().equals(entity.getCommand())) {
            diff.put("command", entity.getCommand() + " -> " + request.command());
            entity.setCommand(request.command());
        }
        if (!request.resultParserType().equals(entity.getResultParserType())) {
            diff.put("resultParserType",
                    entity.getResultParserType() + " -> " + request.resultParserType());
            entity.setResultParserType(request.resultParserType());
        }
    }

    private static ScannerConfigDTO toDTO(ScannerConfig entity) {
        return new ScannerConfigDTO(
                entity.getId(),
                entity.getName(),
                entity.getLanguage(),
                Boolean.TRUE.equals(entity.getEnabled()),
                entity.getCommand(),
                entity.getResultParserType(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    // =====================================================================
    // 系统参数管理（B1-D.5）
    // =====================================================================

    @Override
    public SystemParamDTO getParam(String paramKey) {
        if (paramKey == null || paramKey.isBlank()) {
            return null;
        }
        SystemParam row = systemParamMapper.selectByKey(paramKey);
        if (row == null) {
            return null;
        }
        return toDTO(row);
    }

    @Override
    public List<SystemParamDTO> listParams(String prefix) {
        String trimmed = (prefix == null || prefix.isBlank()) ? null : prefix.trim();
        List<SystemParam> rows = systemParamMapper.listByPrefix(trimmed);
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<SystemParamDTO> result = new ArrayList<>(rows.size());
        for (SystemParam row : rows) {
            result.add(toDTO(row));
        }
        return result;
    }

    @Override
    @Transactional
    public SystemParamDTO updateParam(String paramKey, String value) {
        if (paramKey == null || paramKey.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "paramKey 不能为空");
        }
        if (value == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "value 不能为空");
        }
        AuthenticatedUser caller = CurrentUserHolder.requireCurrent();

        SystemParam existing = systemParamMapper.selectByKey(paramKey);
        if (existing == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "参数不存在: " + paramKey);
        }

        // 已知 key 的范围校验（R21.4）
        IntRange range = KNOWN_PARAM_RANGES.get(paramKey);
        if (range != null) {
            int parsed;
            try {
                parsed = Integer.parseInt(value.trim());
            } catch (NumberFormatException ex) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "value 必须是整数: " + paramKey, ex);
            }
            if (!range.contains(parsed)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "value out of range: " + paramKey
                                + " expected [" + range.min() + "," + range.max()
                                + "] but got " + parsed);
            }
        }

        boolean sensitive = Boolean.TRUE.equals(existing.getSensitive());
        String storedValue = sensitive ? tokenEncryptor.encrypt(value) : value;

        // 审计 detail 中的变更前后值：敏感参数双向掩码（R22.5）
        String beforeForAudit = sensitive ? MaskUtils.FULL_MASK : existing.getParamValue();
        String afterForAudit = sensitive ? MaskUtils.FULL_MASK : value;

        existing.setParamValue(storedValue);
        existing.setUpdatedBy(caller.id());
        systemParamMapper.updateById(existing);

        publishAudit(caller, ACTION_SYSTEM_PARAM_UPDATED, RESOURCE_SYSTEM_PARAM,
                paramKey,
                detailOf(
                        "paramKey", paramKey,
                        "sensitive", sensitive,
                        "diff", Map.of("paramValue", beforeForAudit + " -> " + afterForAudit)));

        // R24.3：通过 Redis pub/sub 通知 Worker 60s 内热更新
        publishParamChanged(paramKey, sensitive ? CHANNEL_PAYLOAD_SENSITIVE_CHANGED : value);

        return toDTO(existing);
    }

    /**
     * 发布参数变更通知到 Redis 通道 {@code param-changed:{key}}。
     *
     * <p>失败时仅记日志：DB 已经成功更新，缓存的滞后由 Worker 自身的兜底周期任务（如有）处理。
     */
    private void publishParamChanged(String paramKey, String payload) {
        String channel = CHANNEL_PARAM_CHANGED_PREFIX + paramKey;
        try {
            stringRedisTemplate.convertAndSend(channel, payload);
        } catch (RuntimeException ex) {
            log.warn("publish param-changed failed: channel={} err={}", channel, ex.toString());
        }
    }

    private SystemParamDTO toDTO(SystemParam entity) {
        boolean sensitive = Boolean.TRUE.equals(entity.getSensitive());
        String value = sensitive ? MaskUtils.FULL_MASK : entity.getParamValue();
        return new SystemParamDTO(
                entity.getParamKey(),
                value,
                entity.getDescription(),
                sensitive,
                entity.getUpdatedBy(),
                entity.getUpdatedAt());
    }

    // =====================================================================
    // 公共工具
    // =====================================================================

    private void publishAudit(AuthenticatedUser caller, String action, String resourceType,
                              String resourceId, Map<String, Object> detail) {
        AuditEvent event = AuditEvent.of(
                caller.id(),
                caller.username(),
                action,
                resourceType,
                resourceId,
                null,
                detail);
        eventPublisher.publishEvent(event);
    }

    /** 构造保留插入顺序的 detail Map。可变长键值对参数。 */
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

    /** 简单的整数闭区间，仅供 {@link #KNOWN_PARAM_RANGES} 使用。 */
    private record IntRange(int min, int max) {
        boolean contains(int v) {
            return v >= min && v <= max;
        }
    }

    /** 暴露给同包测试 / 其他子任务（B1-D.5）使用的 known-param 范围只读视图。 */
    static Map<String, IntRange> knownParamRanges() {
        return KNOWN_PARAM_RANGES;
    }
}
