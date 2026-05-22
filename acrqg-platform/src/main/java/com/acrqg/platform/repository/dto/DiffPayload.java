package com.acrqg.platform.repository.dto;

import java.util.Collections;
import java.util.List;

/**
 * Diff 拉取载荷（design.md §6.4 / R10.1）。
 *
 * <p>由 {@link com.acrqg.platform.repository.client.ProviderClient#fetchDiff
 * ProviderClient.fetchDiff} 返回，作为
 * {@link com.acrqg.platform.diff.service.DiffParser#parseFromPayload
 * DiffParser.parseFromPayload} 的输入。
 *
 * <p>本任务（B3-C.3）将 B2-A 阶段定义的占位结构（{@code rawUnifiedDiff} +
 * {@code FileEntry}）替换为更精细的 {@link DiffFilePayload} 列表：每个文件
 * 携带自己的 {@code patch} 文本、增删行数与 SHA。这样：
 * <ol>
 *   <li>避免 DiffParser 需要先把整个 raw unified diff 拆分回单文件——三大平台
 *       的 REST 响应天然按文件返回，直接映射成本最低；</li>
 *   <li>{@code DiffFilePayload} 与 {@code diff_file} DDL 的列结构一致，
 *       DiffParser 写库时 1:1 转换；</li>
 *   <li>属性测试 P7 可以直接基于 {@link DiffFilePayload} 的 patch 与
 *       {@code additions}/{@code deletions} 自洽校验。</li>
 * </ol>
 *
 * <p>Covers: R10.1, R10.2, R10.3。
 *
 * @param files 单文件 diff 列表；构造时拷贝为不可修改 {@link List}
 */
public record DiffPayload(
        List<DiffFilePayload> files
) {

    public DiffPayload {
        files = files == null ? Collections.emptyList() : List.copyOf(files);
    }

    /** 空载荷（无变更文件）。 */
    public static DiffPayload empty() {
        return new DiffPayload(Collections.emptyList());
    }
}
