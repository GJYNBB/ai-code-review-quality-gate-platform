package com.acrqg.platform.diff.domain;

/**
 * 变更类型字典（design.md §6.4 / §7.2）。
 *
 * <p>对应数据库列 {@code diff_file.change_type} 的 CHECK 约束取值：
 * <ul>
 *   <li>{@link #ADDED}    —— 新增文件；</li>
 *   <li>{@link #MODIFIED} —— 修改文件；</li>
 *   <li>{@link #DELETED}  —— 删除文件；</li>
 *   <li>{@link #RENAMED}  —— 重命名（可能同时含内容变更）；
 *       此时 {@code DiffFile.oldPath} 写入变更前路径。</li>
 * </ul>
 *
 * <p>三大平台 status 取值映射（B3-C.3 在各 ProviderClient 中完成）：
 * <pre>
 *  GitHub  status:    added    -> ADDED
 *                     modified -> MODIFIED
 *                     removed  -> DELETED
 *                     renamed  -> RENAMED
 *  GitLab  flags:     new_file=true       -> ADDED
 *                     deleted_file=true   -> DELETED
 *                     renamed_file=true   -> RENAMED
 *                     otherwise           -> MODIFIED
 *  Gitee   status:    added/modified/removed/renamed (与 GitHub 一致)
 * </pre>
 *
 * <p>Covers: R10.2, R10.3。
 */
public enum ChangeType {

    /** 新增文件。 */
    ADDED,

    /** 修改文件（行内增删）。 */
    MODIFIED,

    /** 删除文件。 */
    DELETED,

    /** 重命名文件（可同时含内容变更）。 */
    RENAMED;

    /**
     * 从平台原生字符串解析为 {@link ChangeType}（大小写不敏感）。
     *
     * <p>支持取值：{@code added/modified/removed/renamed}（GitHub / Gitee）；
     * 未识别字符串退化为 {@link #MODIFIED}（保守选择，避免解析失败丢失变更）。
     *
     * @param raw 平台原生字符串；可为 {@code null}
     * @return 解析结果，永不为 {@code null}
     */
    public static ChangeType fromGithubLike(String raw) {
        if (raw == null) {
            return MODIFIED;
        }
        return switch (raw.trim().toLowerCase()) {
            case "added", "new", "create", "created" -> ADDED;
            case "removed", "deleted", "delete" -> DELETED;
            case "renamed", "rename" -> RENAMED;
            case "modified", "changed", "update", "updated" -> MODIFIED;
            default -> MODIFIED;
        };
    }

    /** 从 GitLab merge request changes 的三个布尔标志推断。 */
    public static ChangeType fromGitlabFlags(boolean newFile, boolean deletedFile, boolean renamedFile) {
        if (deletedFile) {
            return DELETED;
        }
        if (newFile) {
            return ADDED;
        }
        if (renamedFile) {
            return RENAMED;
        }
        return MODIFIED;
    }
}
