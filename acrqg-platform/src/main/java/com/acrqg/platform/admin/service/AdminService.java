package com.acrqg.platform.admin.service;

import com.acrqg.platform.admin.dto.ModelConfigCreateRequest;
import com.acrqg.platform.admin.dto.ModelConfigDTO;
import com.acrqg.platform.admin.dto.ModelConfigUpdateRequest;
import com.acrqg.platform.admin.dto.ScannerConfigDTO;
import com.acrqg.platform.admin.dto.ScannerConfigRequest;
import com.acrqg.platform.admin.dto.SystemParamDTO;
import java.util.List;

/**
 * 系统管理服务（M10）。
 *
 * <p>面向 {@link com.acrqg.platform.admin.controller.AdminController}；所有方法
 * 仅 {@code SYSTEM_ADMIN} 角色可调用，由 Controller 层
 * {@code @RequirePermission(role = Role.SYSTEM_ADMIN)} 拦截。
 *
 * <p>本接口聚合三个子域，避免每个子域单独建一个 Service 接口造成过度拆分：
 * <ol>
 *   <li><b>模型管理</b>（R21.1, R21.2, R21.5, R23.2）：
 *       {@link #createModel} / {@link #listModels} / {@link #getModel} /
 *       {@link #updateModel} / {@link #enableDisableModel} /
 *       {@link #decryptModelApiKey}。</li>
 *   <li><b>扫描器管理</b>（R21.3）：
 *       {@link #upsertScanner} / {@link #listScanners} / {@link #getScanner}。</li>
 *   <li><b>系统参数管理</b>（R21.4, R21.5, R22.1, R24.3）：
 *       {@link #getParam} / {@link #listParams} / {@link #updateParam}。</li>
 * </ol>
 *
 * <p>Covers: R21, R22.1, R23.2, R23.3, R24.3。
 */
public interface AdminService {

    // ---------------------------------------------------------------------
    // 模型管理（B1-D.3）
    // ---------------------------------------------------------------------

    /**
     * 创建 AI 模型配置。
     *
     * <p>语义：
     * <ul>
     *   <li>{@code apiKey} 经 {@code TokenEncryptor.encrypt} 加密后存储；</li>
     *   <li>返回 DTO 中 {@code apiKeyMasked="****"}（R21.2 / R23.3）；</li>
     *   <li>发布 {@code MODEL_CONFIG_CREATED} 审计事件，detail 中 apiKey 字段以掩码呈现。</li>
     * </ul>
     *
     * @param request 创建请求
     * @return 已脱敏的 DTO
     */
    ModelConfigDTO createModel(ModelConfigCreateRequest request);

    /**
     * 列表返回全部模型配置（按 id 升序），所有 {@code apiKeyMasked} 均为 {@code "****"}。
     */
    List<ModelConfigDTO> listModels();

    /**
     * 按主键返回单个模型；未命中抛 {@code BusinessException(VALIDATION_ERROR)}。
     */
    ModelConfigDTO getModel(Long id);

    /**
     * 更新模型配置。
     *
     * <p>PATCH 语义：仅更新非 {@code null} 的字段。当 {@code apiKey} 提供时
     * 重新加密并落库；不提供时保留原密文。发布 {@code MODEL_CONFIG_UPDATED}
     * 审计事件，detail 中 apiKey 始终掩码。
     *
     * @param id      模型主键
     * @param request 更新请求
     * @return 已脱敏的 DTO
     */
    ModelConfigDTO updateModel(Long id, ModelConfigUpdateRequest request);

    /**
     * 启用 / 禁用模型。
     *
     * <p>独立接口便于前端开关交互；语义等同 {@link #updateModel(Long, ModelConfigUpdateRequest)}
     * 仅传 {@code enabled} 字段。发布 {@code MODEL_CONFIG_UPDATED} 审计事件。
     *
     * @param id      模型主键
     * @param enabled true=启用, false=禁用
     * @return 已脱敏的 DTO
     */
    ModelConfigDTO enableDisableModel(Long id, boolean enabled);

    /**
     * <b>仅供 B3-E AI 客户端使用的内部接口</b>：解密返回模型 apiKey 明文。
     *
     * <p>调用约束：
     * <ul>
     *   <li>仅可在 AI 客户端发起 HTTPS 请求前的瞬时调用，明文不得日志输出；</li>
     *   <li>调用方必须确保返回值仅在 HTTP Authorization 头中使用，不进入任何
     *       业务持久化（DB / Redis / MQ）；</li>
     *   <li>不通过 REST 端点暴露——本接口不会被 {@code AdminController} 调用。</li>
     * </ul>
     *
     * @param modelId 模型主键
     * @return apiKey 明文；模型不存在抛 {@code BusinessException(VALIDATION_ERROR)}
     */
    String decryptModelApiKey(Long modelId);

    // ---------------------------------------------------------------------
    // 扫描器管理（B1-D.4）
    // ---------------------------------------------------------------------

    /**
     * 以 {@code name} 为业务键 upsert 扫描器配置。
     *
     * <p>已存在则更新 {@code language}/{@code enabled}/{@code command}/
     * {@code resultParserType}；不存在则插入。发布 {@code SCANNER_CONFIG_UPSERTED}
     * 审计事件。
     */
    ScannerConfigDTO upsertScanner(ScannerConfigRequest request);

    /** 列表返回全部扫描器配置（按 id 升序）。 */
    List<ScannerConfigDTO> listScanners();

    /** 按主键返回扫描器；未命中抛 {@code BusinessException(VALIDATION_ERROR)}。 */
    ScannerConfigDTO getScanner(Long id);

    // ---------------------------------------------------------------------
    // 系统参数管理（B1-D.5）
    // ---------------------------------------------------------------------

    /**
     * 按 key 取单个参数；未命中返回 {@code null}（不抛异常，便于上层做缺失兜底）。
     *
     * <p>当参数 {@code sensitive=true} 时，返回的 {@code paramValue} 已替换为
     * {@code "****"}（R23.3）。
     */
    SystemParamDTO getParam(String paramKey);

    /**
     * 按可选前缀列出系统参数；{@code prefix} 为 {@code null}/空 表示全表。
     *
     * <p>敏感参数 {@code paramValue} 已脱敏。
     */
    List<SystemParamDTO> listParams(String prefix);

    /**
     * 更新系统参数。
     *
     * <p>语义：
     * <ul>
     *   <li>已知 key 的类型校验：
     *     <ul>
     *       <li>{@code review.worker.concurrency}：整数 [1, 32]；</li>
     *       <li>{@code ai.review.timeout.seconds}：整数 [10, 300]；</li>
     *       <li>{@code diff.maxLinesPerFile}：整数 [100, 50000]；</li>
     *       <li>越界抛 {@code BusinessException(VALIDATION_ERROR, "value out of range")}。</li>
     *     </ul>
     *   </li>
     *   <li>{@code sensitive=true} 的参数：先经 {@code TokenEncryptor.encrypt}
     *       加密后落库；</li>
     *   <li>发布 {@code SYSTEM_PARAM_UPDATED} 审计事件，detail 中携带变更前后值
     *       （敏感参数双向掩码）；</li>
     *   <li>通过 Redis pub/sub 发布 {@code param-changed:{key}} 通道消息，供
     *       Worker 在 60s 内热更新（R24.3）。</li>
     * </ul>
     *
     * @param paramKey 参数键
     * @param value    新值（明文；sensitive 由数据库 sensitive 列决定）
     * @return 已脱敏的 DTO
     */
    SystemParamDTO updateParam(String paramKey, String value);
}
