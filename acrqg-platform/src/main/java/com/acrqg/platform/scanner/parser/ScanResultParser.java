package com.acrqg.platform.scanner.parser;

import com.acrqg.platform.code_issue.domain.CodeIssue;
import com.acrqg.platform.scanner.process.ScannerOutput;
import java.util.List;

/**
 * 扫描器结果解析器 SPI（design.md §11.3）。
 *
 * <p>每个实现负责把一个特定工具（CHECKSTYLE_XML / ESLINT_JSON / PYLINT_JSON /
 * SEMGREP_JSON）的子进程输出转换为 {@link CodeIssue} 列表。
 *
 * <p>{@link #type()} 返回的字符串与 {@code scanner_config.result_parser_type}
 * 的取值一一对应，便于 {@link com.acrqg.platform.scanner.adapter.StaticScannerAdapter}
 * 通过 type 路由到具体实现。
 *
 * <p>解析失败约定：解析器在遇到无法识别的输出时应尽量"宽容"——
 * 跳过单条非法记录但保留其他；只有当整体输出无法解析（如 JSON 结构错误）
 * 时才抛 {@link RuntimeException}，由调用方决定是否重试或忽略。
 *
 * <p>Covers: R11.2, R11.3。
 */
public interface ScanResultParser {

    /** 与 {@code scanner_config.result_parser_type} 一致的类型字符串。 */
    String type();

    /**
     * 把扫描器输出解析为 CodeIssue 列表。
     *
     * <p>实现约定：
     * <ul>
     *   <li>仅填充 {@code filePath} / {@code lineNo} / {@code ruleCode} /
     *       {@code description} / {@code suggestion} / {@code severity} 字段；</li>
     *   <li>{@code source} 由 {@link com.acrqg.platform.scanner.adapter.StaticScannerAdapter}
     *       统一设为 {@code SAST}；{@code status} 默认 {@code NEW}；
     *       {@code taskId} 由 Orchestrator 在 batch insert 前回填。</li>
     * </ul>
     *
     * @param output 扫描器子进程输出
     * @return 解析结果（不为 {@code null}，可能为空）
     */
    List<CodeIssue> parse(ScannerOutput output);
}
