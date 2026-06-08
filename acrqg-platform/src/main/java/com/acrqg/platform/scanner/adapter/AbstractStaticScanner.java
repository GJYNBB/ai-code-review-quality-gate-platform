package com.acrqg.platform.scanner.adapter;

import com.acrqg.platform.admin.domain.ScannerConfig;
import com.acrqg.platform.admin.repository.ScannerConfigMapper;
import com.acrqg.platform.code_issue.domain.CodeIssue;
import com.acrqg.platform.diff.domain.DiffFile;
import com.acrqg.platform.scanner.parser.ScanResultParser;
import com.acrqg.platform.scanner.parser.ScanResultParserRegistry;
import com.acrqg.platform.scanner.process.ScannerOutput;
import com.acrqg.platform.scanner.process.ScannerProcessException;
import com.acrqg.platform.scanner.process.ScannerProcessRunner;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 4 个 SAST 扫描器适配器的抽象基类，封装共同流程：
 * <ol>
 *   <li>{@code @PostConstruct} 时按 {@link #name()} 从 {@code scanner_config} 加载并缓存配置；</li>
 *   <li>{@link #isAvailable()} 直接返回 {@link ScannerConfig#getEnabled()}（避免运行时 exec）；</li>
 *   <li>{@link #scan(ScanContext)} 按变更文件构造命令、调用 {@link ScannerProcessRunner}、
 *       通过 {@link ScanResultParserRegistry} 选择解析器、回填 {@code taskId}。</li>
 * </ol>
 *
 * <p>子类只需声明 {@link #name()} 与 {@link #supportedLanguages()}。
 *
 * <p>Covers: R11.1, R11.2, R11.3, R11.4。
 */
public abstract class AbstractStaticScanner implements StaticScannerAdapter {

    private static final Logger log = LoggerFactory.getLogger(AbstractStaticScanner.class);

    private final ScannerConfigMapper scannerConfigMapper;
    private final ScannerProcessRunner processRunner;
    private final ScanResultParserRegistry parserRegistry;

    /** 启动时加载并缓存的扫描器配置；若 DB 中不存在则保持 {@code null}。 */
    private volatile ScannerConfig cachedConfig;

    protected AbstractStaticScanner(ScannerConfigMapper scannerConfigMapper,
                                    ScannerProcessRunner processRunner,
                                    ScanResultParserRegistry parserRegistry) {
        this.scannerConfigMapper = scannerConfigMapper;
        this.processRunner = processRunner;
        this.parserRegistry = parserRegistry;
    }

    @PostConstruct
    void loadConfig() {
        try {
            this.cachedConfig = scannerConfigMapper.selectByName(name());
            if (cachedConfig == null) {
                log.warn("scanner config not found for name={}; will treat as unavailable", name());
            } else {
                log.info("scanner loaded: name={} language={} enabled={} parser={}",
                        cachedConfig.getName(), cachedConfig.getLanguage(),
                        cachedConfig.getEnabled(), cachedConfig.getResultParserType());
            }
        } catch (RuntimeException ex) {
            log.warn("scanner config load failed for {}: {}", name(), ex.toString());
            this.cachedConfig = null;
        }
    }

    /** 配置缓存的访问器（也开放给测试）。 */
    public ScannerConfig getCachedConfig() {
        return cachedConfig;
    }

    @Override
    public boolean isAvailable() {
        ScannerConfig cfg = this.cachedConfig;
        return cfg != null && Boolean.TRUE.equals(cfg.getEnabled());
    }

    @Override
    public List<CodeIssue> scan(ScanContext ctx) {
        ScannerConfig cfg = this.cachedConfig;
        if (cfg == null || !Boolean.TRUE.equals(cfg.getEnabled())) {
            log.debug("{} disabled or not configured, skip", name());
            return Collections.emptyList();
        }
        List<String> files = applicableFiles(ctx);
        if (files.isEmpty()) {
            log.debug("{} no applicable files, skip", name());
            return Collections.emptyList();
        }
        Path workdir = ctx.workdir();
        Path tempDir = null;
        try {
            if (workdir == null) {
                throw new ScannerProcessException(
                        "scanner requires a real checked-out source workdir; fragment-only diff input is not safe: " + name());
            }
            ScannerOutput output = processRunner.run(cfg, workdir, files);
            ScanResultParser parser = parserRegistry.get(cfg.getResultParserType());
            if (parser == null) {
                throw new ScannerProcessException(
                        "no parser for result_parser_type=" + cfg.getResultParserType()
                                + " (scanner=" + name() + ")");
            }
            List<CodeIssue> issues = parser.parse(output);
            if (issues == null) {
                return Collections.emptyList();
            }
            // 回填 taskId
            for (CodeIssue issue : issues) {
                issue.setTaskId(ctx.taskId());
            }
            return issues;
        } finally {
            if (tempDir != null) {
                deleteSilently(tempDir);
            }
        }
    }

    /**
     * 返回当前任务中本扫描器适用的文件路径（已剔除 oversized；仅保留对应语言扩展名）。
     *
     * <p>过滤规则：
     * <ol>
     *   <li>剔除 {@code oversized=true}（R11.5）；</li>
     *   <li>调用 {@link #fileMatches(String)} 按扩展名筛选（如 Checkstyle 仅 *.java）；</li>
     *   <li>按字典序排序保证可重现。</li>
     * </ol>
     */
    protected List<String> applicableFiles(ScanContext ctx) {
        return ctx.changedFiles().stream()
                .filter(f -> !Boolean.TRUE.equals(f.getOversized()))
                .filter(f -> !"DELETED".equalsIgnoreCase(f.getChangeType()))
                .map(DiffFile::getFilePath)
                .filter(java.util.Objects::nonNull)
                .filter(this::fileMatches)
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toUnmodifiableList());
    }

    /** 子类按扩展名过滤文件；默认全部接受（Semgrep）。 */
    protected boolean fileMatches(String filePath) {
        return true;
    }

    private static void deleteSilently(Path dir) {
        try {
            try (var stream = Files.walk(dir)) {
                stream.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException ignored) {
                                // best effort
                            }
                        });
            }
        } catch (IOException ignored) {
            // best effort
        }
    }
}
