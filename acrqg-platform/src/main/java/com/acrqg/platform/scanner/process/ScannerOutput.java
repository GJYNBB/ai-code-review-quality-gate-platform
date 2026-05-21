package com.acrqg.platform.scanner.process;

import java.nio.file.Path;

/**
 * 扫描器子进程执行结果（design.md §11.3）。
 *
 * <p>由 {@link ScannerProcessRunner#run} 返回。捕获子进程的退出码、标准输出 /
 * 错误流以及（可选的）结果文件路径。
 *
 * <p>各结果解析器（{@link com.acrqg.platform.scanner.parser.ScanResultParser}）
 * 优先读取 {@link #outputFile} 内容；若 {@link #outputFile} 为 {@code null}
 * 或不存在，则退化为读取 {@link #stdout}（如 Pylint 的 JSON 走 {@code stdout}
 * 重定向场景）。
 *
 * <p>Covers: R11.1, R11.2。
 *
 * @param exitCode  子进程退出码（0 表示成功；扫描器在发现问题时通常返回非 0，调用方需容错）
 * @param stdout    标准输出（已以 UTF-8 解码）
 * @param stderr    标准错误（已以 UTF-8 解码）
 * @param outputFile 结果文件路径；命令模板未包含 {@code {output}} 占位时为 {@code null}
 */
public record ScannerOutput(int exitCode, String stdout, String stderr, Path outputFile) {
}
