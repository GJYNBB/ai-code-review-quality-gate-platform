package com.acrqg.platform.scanner.adapter;

import com.acrqg.platform.admin.repository.ScannerConfigMapper;
import com.acrqg.platform.scanner.parser.ScanResultParserRegistry;
import com.acrqg.platform.scanner.process.ScannerProcessRunner;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Semgrep 适配器（通用安全 / 跨语言）。
 *
 * <p>{@link #supportedLanguages()} 返回 {@code Set.of("any")} —— ScannerOrchestrator
 * 在 {@code project.language} 不匹配其他扫描器时仍会运行 Semgrep（R11.1：
 * 通用安全检查）。本适配器接受所有变更文件路径，由 Semgrep 自身的语言识别决定
 * 哪些规则适用。
 *
 * <p>Covers: R11.1, R11.2。
 */
@Component
public class SemgrepScanner extends AbstractStaticScanner {

    public static final String NAME = "semgrep";

    public SemgrepScanner(ScannerConfigMapper scannerConfigMapper,
                          ScannerProcessRunner processRunner,
                          ScanResultParserRegistry parserRegistry) {
        super(scannerConfigMapper, processRunner, parserRegistry);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Set<String> supportedLanguages() {
        return Set.of("any");
    }

    /** Semgrep 接受所有变更文件，规则适配由 Semgrep 自身决定。 */
    @Override
    protected boolean fileMatches(String filePath) {
        return filePath != null && !filePath.isBlank();
    }
}
