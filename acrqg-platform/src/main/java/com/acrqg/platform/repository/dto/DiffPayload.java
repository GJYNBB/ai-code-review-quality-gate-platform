package com.acrqg.platform.repository.dto;

import java.util.Collections;
import java.util.List;

/**
 * 原始 diff 载荷（design.md §6.4 / R10.1）。
 *
 * <p>由 {@link com.acrqg.platform.repository.client.ProviderClient#fetchDiff
 * ProviderClient.fetchDiff} 返回，作为 {@code DiffParser} 的输入。本任务（B2-A）
 * 仅定义结构占位，具体填充语义由 B3-C 实现，因此各字段允许为空集合 / 空字符串。
 *
 * <p>字段语义：
 * <ul>
 *   <li>{@code provider}：源平台代码（GITHUB / GITLAB / GITEE）；</li>
 *   <li>{@code rawUnifiedDiff}：完整 unified diff 文本（用于
 *       {@code DiffParser.parseFromPayload} 的属性测试 P7）；</li>
 *   <li>{@code files}：从 ProviderClient REST 响应中提取的文件元信息列表，
 *       可用于校验 {@code rawUnifiedDiff} 的解析结果；</li>
 *   <li>{@code totalAdditions} / {@code totalDeletions}：仓库平台返回的总增删行数，
 *       便于属性测试 P7 检查 {@code DiffParser.parseFromPayload} 的一致性。</li>
 * </ul>
 *
 * <p>Covers: R10.1（结构）；填充由 B3-C 完成。
 *
 * @param provider        源平台
 * @param rawUnifiedDiff  完整 unified diff 文本
 * @param files           文件元信息列表
 * @param totalAdditions  总增加行数
 * @param totalDeletions  总删除行数
 */
public record DiffPayload(
        String provider,
        String rawUnifiedDiff,
        List<FileEntry> files,
        long totalAdditions,
        long totalDeletions
) {

    public DiffPayload {
        files = files == null ? Collections.emptyList() : List.copyOf(files);
    }

    /**
     * 单文件元信息条目。
     *
     * @param path      文件路径（变更前后路径不同时为变更后路径）
     * @param oldPath   重命名场景下的变更前路径；通常为 {@code null}
     * @param status    {@code added} / {@code modified} / {@code removed} / {@code renamed}
     * @param additions 该文件新增行数
     * @param deletions 该文件删除行数
     */
    public record FileEntry(
            String path,
            String oldPath,
            String status,
            long additions,
            long deletions
    ) {
    }
}
