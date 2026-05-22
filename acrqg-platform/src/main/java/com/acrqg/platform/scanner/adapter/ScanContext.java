package com.acrqg.platform.scanner.adapter;

import com.acrqg.platform.admin.domain.ScannerConfig;
import com.acrqg.platform.diff.domain.DiffFile;
import com.acrqg.platform.task.domain.ReviewTask;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/**
 * 单次扫描的执行上下文（design.md §6.5 / §11.1）。
 *
 * <p>由 {@link com.acrqg.platform.scanner.ScannerOrchestrator} 在调度
 * {@link StaticScannerAdapter#scan(ScanContext)} 前构造。承载该次扫描所需的
 * 所有最小元数据：
 * <ul>
 *   <li>{@code taskId}        —— 当前评审任务主键，用于审计 / 写 task_log；</li>
 *   <li>{@code task}          —— 评审任务 DO 快照（包含 commitSha / projectId 等）；</li>
 *   <li>{@code changedFiles}  —— 变更文件列表（已剔除 oversized 行）；</li>
 *   <li>{@code workdir}       —— 进程隔离时的临时工作目录（可选；适配器可不使用，直接用 file 绝对路径）；</li>
 *   <li>{@code scannerConfig} —— 当前扫描器的 ScannerConfig（含 command 模板与 result_parser_type）。</li>
 * </ul>
 *
 * <p>本 record 不可变；{@link #changedFiles()} 通过 {@link List#copyOf(java.util.Collection)}
 * 防御性拷贝，跨线程使用安全。
 *
 * <p>Covers: R11.1, R11.2, R11.5。
 *
 * @param taskId        评审任务主键
 * @param task          评审任务 DO；调用方保证非 {@code null}
 * @param changedFiles  变更文件列表；{@code null} 视为空列表
 * @param workdir       工作目录；可为 {@code null}
 * @param scannerConfig 扫描器配置；调用方保证非 {@code null}
 */
public record ScanContext(
        long taskId,
        ReviewTask task,
        List<DiffFile> changedFiles,
        Path workdir,
        ScannerConfig scannerConfig
) {
    public ScanContext {
        changedFiles = changedFiles == null ? Collections.emptyList() : List.copyOf(changedFiles);
    }
}
