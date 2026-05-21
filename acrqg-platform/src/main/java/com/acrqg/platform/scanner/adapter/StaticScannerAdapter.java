package com.acrqg.platform.scanner.adapter;

import com.acrqg.platform.code_issue.domain.CodeIssue;
import java.util.List;
import java.util.Set;

/**
 * 静态扫描适配器 SPI（design.md §6.5 / §11.1）。
 *
 * <p>每个扫描器（Checkstyle / ESLint / Pylint / Semgrep ...）以一个 Spring bean
 * 注册到 {@link com.acrqg.platform.scanner.ScannerOrchestrator}。
 *
 * <p>语义约定：
 * <ul>
 *   <li>{@link #name()} —— 唯一名称，与 {@code scanner_config.name} 一致
 *       （{@code checkstyle} / {@code eslint} / {@code pylint} / {@code semgrep}）；</li>
 *   <li>{@link #supportedLanguages()} —— 支持的语言集合（小写，例如
 *       {@code java} / {@code javascript} / {@code typescript} / {@code python}）；
 *       {@code semgrep} 返回单元素 {@code "any"} 表示通用扫描器；</li>
 *   <li>{@link #isAvailable()} —— 是否可用；当扫描器配置 disabled 或
 *       命令在容器内未安装时返回 {@code false}。Orchestrator 在调用 {@link #scan} 前
 *       会先过滤掉 {@code !isAvailable()} 的适配器。</li>
 *   <li>{@link #scan(ScanContext)} —— 执行扫描并把工具原始输出归一化为
 *       {@link CodeIssue} 列表（source=SAST / status=NEW / severity 经
 *       {@link com.acrqg.platform.scanner.SeverityMapper} 归一化）。</li>
 * </ul>
 *
 * <p>错误处理：实现可抛任意 {@link RuntimeException}。Orchestrator 会捕获
 * 单个适配器的异常并写 WARN 级 task_log，不影响其他扫描器（R11.4）。
 *
 * <p>Covers: R11.1, R11.2, R11.3, R11.4。
 */
public interface StaticScannerAdapter {

    /** 扫描器唯一名称（与 scanner_config.name 一致）。 */
    String name();

    /** 支持的语言集合（小写）；通用扫描器返回 {@code Set.of("any")}。 */
    Set<String> supportedLanguages();

    /** 当前扫描器是否可用；不可用时 Orchestrator 会跳过。 */
    boolean isAvailable();

    /**
     * 执行扫描。返回值列表元素必须满足：
     * <ul>
     *   <li>{@code taskId} = ctx.taskId()</li>
     *   <li>{@code source} = "SAST"</li>
     *   <li>{@code status} = "NEW"</li>
     *   <li>{@code severity} 已经过 {@link com.acrqg.platform.scanner.SeverityMapper} 归一化</li>
     * </ul>
     *
     * @param ctx 扫描上下文
     * @return 归一化后的 CodeIssue 列表（可能为空，但不应返回 {@code null}）
     */
    List<CodeIssue> scan(ScanContext ctx);
}
