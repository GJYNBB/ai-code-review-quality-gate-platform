package com.acrqg.platform.ai.filter;

import java.util.Collections;
import java.util.List;

/**
 * 进入 AI 评审客户端前的"原始"载荷快照。
 *
 * <p>由 {@code AiReviewService} 在调用 {@link SensitiveFilter} 之前构造：
 * <ul>
 *   <li>{@link #files} —— 当前任务的变更文件清单（路径 + patch 文本）；</li>
 *   <li>{@link #language} —— 项目语言（{@code Java/JavaScript/...}），写入 prompt；</li>
 *   <li>{@link #gateMetrics} —— 门禁关注指标列表（如 {@code critical_issue_count,
 *       ai_risk_score}），写入 prompt 引导模型聚焦；</li>
 *   <li>{@link #taskId} —— 用于错误诊断写 task_log 时定位。</li>
 * </ul>
 *
 * <p>本类是不可变 {@code record}：构造时对 {@code files} / {@code gateMetrics}
 * 做防御性拷贝并包装为 {@link Collections#unmodifiableList}，避免发布方在
 * filter 流水线中途意外修改。
 *
 * <p>Covers: R12.1, R12.2, R23.4。
 *
 * @param taskId      任务主键（用于日志定位；filter 不依赖此字段做业务判定）
 * @param language    项目语言，prompt 模板替换 {@code {language}}
 * @param gateMetrics 门禁关注指标；prompt 模板替换 {@code {metrics}}
 * @param files       变更文件清单
 */
public record AiReviewPayload(
        long taskId,
        String language,
        List<String> gateMetrics,
        List<FileEntry> files) {

    public AiReviewPayload {
        gateMetrics = gateMetrics == null
                ? Collections.emptyList()
                : List.copyOf(gateMetrics);
        files = files == null
                ? Collections.emptyList()
                : List.copyOf(files);
    }

    /**
     * 单个变更文件的最小快照，仅承载 {@link SensitiveFilter} / {@code PromptBuilder}
     * 所必需的字段。
     *
     * <p>{@code patch} 字段保存的是 unified diff 文本（已由 B3-C 的
     * {@code DiffParser} 抓取并落到 {@code diff_file.patch} 列）。
     *
     * @param filePath  文件路径（变更后路径；RENAMED 取新路径）
     * @param patch     unified diff 文本；可能为空字符串（DELETED 文件）
     * @param oversized 是否超过 {@code diff.maxLinesPerFile}；filter 不消费该字段，
     *                  服务层在构造 payload 时已剔除 oversized=true 的文件
     */
    public record FileEntry(String filePath, String patch, boolean oversized) {

        public FileEntry {
            if (filePath == null) {
                throw new IllegalArgumentException("filePath is required");
            }
            if (patch == null) {
                patch = "";
            }
        }
    }
}
