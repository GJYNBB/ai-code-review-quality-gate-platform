package com.acrqg.platform.gate.service;

import com.acrqg.platform.gate.dto.QualityGateDTO;
import com.acrqg.platform.gate.dto.QualityGateSaveRequest;
import java.util.List;

/**
 * 质量门禁服务（M07，仅 CRUD，不含执行引擎）。
 *
 * <p>对齐 design.md §6.7：
 * <pre>
 * public interface QualityGateService {
 *     QualityGateDTO save(Long projectId, QualityGateSaveRequest req);    // R13
 *     QualityGateDTO getEnabled(Long projectId);
 * }
 * </pre>
 *
 * <p>本接口在 design 基础上扩展了 {@link #listVersions} / {@link #getVersion} /
 * {@link #getDefaultTemplate} 三个方法，覆盖 tasks B2-B.3 的全部子需求；判定引擎
 * （{@code GateRuleEngine}）在 B3-F 单独实现，本接口不感知。
 *
 * <p>异常约定：
 * <ul>
 *   <li>{@link #save}：rule 的 metric / operator / severity 不在合法集合内，
 *       抛 {@code BusinessException(GATE_RULE_INVALID)} 并在 details 中携带
 *       {@code rules[i].metric} 形式的字段路径（R13.3）。</li>
 *   <li>{@link #save}：项目不存在时抛 {@code BusinessException(VALIDATION_ERROR)}。</li>
 *   <li>{@link #getEnabled}：项目下无 enabled 版本时返回 {@code null}（不抛异常，
 *       便于前端做 "未配置" 的兜底交互；GET 接口仍返回 {@code ApiResponse.success(null)}）。</li>
 *   <li>{@link #getVersion}：版本不存在时抛 {@code BusinessException(VALIDATION_ERROR)}。</li>
 * </ul>
 *
 * <p>{@link #save} 被 {@code @Transactional} 修饰：在同一事务内
 *   先 UPDATE 旧版本 enabled=false → INSERT 新版本 enabled=true →
 *   批量 INSERT 规则。失败回滚不会留下"半启用"状态。
 *
 * <p>Covers: R13.1, R13.2, R13.3, R13.4, R13.5, R13.6。
 */
public interface QualityGateService {

    /**
     * 保存（覆盖）质量门禁配置。
     *
     * <p>语义：每次保存生成一个新版本（{@code version = max(version)+1}）；保存成功后
     * 旧的 enabled 版本被翻为 disabled，新版本 enabled=true（同事务）。
     * 写审计 {@code QUALITY_GATE_SAVED}（R22.1）。
     *
     * @param projectId 项目主键
     * @param request   保存请求
     * @return 已保存的版本 DTO（含规则列表）
     */
    QualityGateDTO save(Long projectId, QualityGateSaveRequest request);

    /**
     * 取项目当前 enabled 版本及其规则。
     *
     * @param projectId 项目主键
     * @return 启用版本 DTO；不存在返回 {@code null}
     */
    QualityGateDTO getEnabled(Long projectId);

    /**
     * 列出项目所有版本（不含规则；规则用 {@link #getVersion} 单独取）。
     *
     * <p>按 {@code version DESC} 排序，最新版本在前。
     *
     * @param projectId 项目主键
     * @return 版本列表，可能为空
     */
    List<QualityGateDTO> listVersions(Long projectId);

    /**
     * 按门禁主键取单个版本及其规则。
     *
     * @param gateId 门禁版本主键
     * @return 版本 DTO；不存在抛 {@code VALIDATION_ERROR}
     */
    QualityGateDTO getVersion(Long gateId);

    /**
     * 返回默认模板（design §13.5 / R13.5）：
     * <ol>
     *   <li>{@code critical_issue_count <= 0} BLOCKER</li>
     *   <li>{@code test_coverage >= 70} BLOCKER</li>
     *   <li>{@code ai_risk_score <= 80} WARN</li>
     * </ol>
     *
     * <p>返回的 DTO {@code id} / {@code projectId} / {@code version} / {@code createdBy}
     * / {@code createdAt} 均为 {@code null}，{@code enabled} 为 {@code false}（未启用）。
     * 前端 "使用模板" 按钮加载该 DTO 后，用户可调整后再走 {@link #save}。
     *
     * @return 内存对象，永不为 {@code null}
     */
    QualityGateDTO getDefaultTemplate();
}
