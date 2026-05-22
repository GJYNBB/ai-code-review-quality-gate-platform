package com.acrqg.platform.diff.repository;

/**
 * {@code diff_file} 任务级聚合统计行（POJO）。
 *
 * <p>由 {@link DiffFileMapper#sumByTask(Long)} 返回；用于
 * {@link com.acrqg.platform.diff.service.impl.DiffParserImpl} 在写完
 * 单文件后回填 {@code review_task} 的统计字段，以及 Report 模块的快速聚合。
 *
 * <p>所有字段允许为 {@code null}（无任何 diff_file 行时聚合返回 {@code NULL}），
 * Service 层应自行做空值兜底，统一按 0 处理。
 *
 * <p>Covers: R10.2, R10.3, R16.1。
 */
public class DiffFileSummary {

    /** 文件数（含 oversized）。 */
    private Long fileCount;

    /** 全任务新增行数。 */
    private Long totalAddedLines;

    /** 全任务删除行数。 */
    private Long totalDeletedLines;

    /** 全任务总变更行数（{@code Σ total_changed_lines}）。 */
    private Long totalChangedLines;

    public DiffFileSummary() {
        // for MyBatis
    }

    public Long getFileCount() {
        return fileCount;
    }

    public void setFileCount(Long fileCount) {
        this.fileCount = fileCount;
    }

    public Long getTotalAddedLines() {
        return totalAddedLines;
    }

    public void setTotalAddedLines(Long totalAddedLines) {
        this.totalAddedLines = totalAddedLines;
    }

    public Long getTotalDeletedLines() {
        return totalDeletedLines;
    }

    public void setTotalDeletedLines(Long totalDeletedLines) {
        this.totalDeletedLines = totalDeletedLines;
    }

    public Long getTotalChangedLines() {
        return totalChangedLines;
    }

    public void setTotalChangedLines(Long totalChangedLines) {
        this.totalChangedLines = totalChangedLines;
    }
}
