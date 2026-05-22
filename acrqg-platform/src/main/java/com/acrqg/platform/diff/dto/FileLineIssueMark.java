package com.acrqg.platform.diff.dto;

/**
 * 行级问题标记（B4-B Report 模块输入）。
 *
 * <p>由 {@code ReportService} 在聚合 {@code code_issue} 后产生，传入
 * {@link com.acrqg.platform.diff.service.DiffViewService#markIssueLines}
 * 标记 diff view 中对应行的问题数。
 *
 * <p>Covers: R16.4。
 *
 * @param filePath   文件路径（必须与 {@code diff_file.file_path} 一致）
 * @param lineNo     新文件中的行号（与 {@link com.acrqg.platform.diff.domain.DiffLine#newLineNo()} 比较）
 * @param issueCount 该行的问题数
 */
public record FileLineIssueMark(
        String filePath,
        int lineNo,
        int issueCount
) {
}
