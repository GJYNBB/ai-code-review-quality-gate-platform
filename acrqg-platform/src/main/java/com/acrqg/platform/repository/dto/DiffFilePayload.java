package com.acrqg.platform.repository.dto;

/**
 * 单文件 diff 载荷（design.md §6.4 / R10.1）。
 *
 * <p>{@link DiffPayload#files()} 的元素，由各 {@code ProviderClient.fetchDiff}
 * 实现从平台 REST 响应映射而来；{@code DiffParser} 在解析阶段消费此结构构造
 * {@link com.acrqg.platform.diff.dto.ChangedFile}。
 *
 * <p>字段语义：
 * <ul>
 *   <li>{@code filePath} ：变更后文件路径（RENAMED 时为新路径）；</li>
 *   <li>{@code oldPath}  ：仅 RENAMED 场景下为变更前路径，其余为 {@code null}；</li>
 *   <li>{@code changeType}：枚举字面量 {@code "ADDED" / "MODIFIED" / "DELETED" / "RENAMED"}，
 *       由 ProviderClient 完成平台原生取值到该枚举的映射；</li>
 *   <li>{@code patch}    ：unified diff 文本（GitHub 单文件 patch、GitLab change.diff、
 *       Gitee patch）。文件被添加 / 删除时仍由平台返回带 hunk 头的 patch；
 *       极个别场景（GitHub 二进制 / 大文件）{@code patch} 为 {@code null}，
 *       此时 {@code additions} 与 {@code deletions} 仍由平台元数据给出。</li>
 *   <li>{@code additions} / {@code deletions} ：平台元数据返回的增删行数；</li>
 *   <li>{@code sha}      ：blob SHA（GitHub / Gitee 文件级 SHA）；GitLab 通常无此字段，
 *       置 {@code null} 即可。仅用于追溯；DiffParser 不强依赖此字段。</li>
 * </ul>
 *
 * <p>Covers: R10.1, R10.2, R10.3。
 *
 * @param filePath   变更后路径（必填）
 * @param oldPath    变更前路径（仅 RENAMED）
 * @param changeType {@code ADDED / MODIFIED / DELETED / RENAMED}
 * @param patch      unified diff 文本，可为 {@code null}
 * @param additions  新增行数（平台元数据）
 * @param deletions  删除行数（平台元数据）
 * @param sha        blob SHA，可为 {@code null}
 */
public record DiffFilePayload(
        String filePath,
        String oldPath,
        String changeType,
        String patch,
        int additions,
        int deletions,
        String sha
) {
}
