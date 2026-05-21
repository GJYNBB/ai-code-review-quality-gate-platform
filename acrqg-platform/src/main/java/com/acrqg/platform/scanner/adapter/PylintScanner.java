package com.acrqg.platform.scanner.adapter;

import com.acrqg.platform.admin.repository.ScannerConfigMapper;
import com.acrqg.platform.scanner.parser.ScanResultParserRegistry;
import com.acrqg.platform.scanner.process.ScannerProcessRunner;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Pylint 适配器（Python）。
 *
 * <p>仅扫描 *.py 文件。命令模板默认通过 stdout 重定向到 {@code {output}}，
 * 由 {@link com.acrqg.platform.scanner.parser.PylintJsonParser} 解析。
 *
 * <p>Covers: R11.1, R11.2。
 */
@Component
public class PylintScanner extends AbstractStaticScanner {

    public static final String NAME = "pylint";

    public PylintScanner(ScannerConfigMapper scannerConfigMapper,
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
        return Set.of("python");
    }

    @Override
    protected boolean fileMatches(String filePath) {
        return filePath != null && filePath.toLowerCase().endsWith(".py");
    }
}
