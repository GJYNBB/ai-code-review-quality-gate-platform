package com.acrqg.platform.scanner.adapter;

import com.acrqg.platform.admin.repository.ScannerConfigMapper;
import com.acrqg.platform.scanner.parser.ScanResultParserRegistry;
import com.acrqg.platform.scanner.process.ScannerProcessRunner;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Checkstyle 适配器（Java）。
 *
 * <p>仅扫描 *.java 文件；通过 {@code scanner_config.command} 模板执行命令，
 * 输出 XML 由 {@link com.acrqg.platform.scanner.parser.CheckstyleXmlParser} 解析。
 *
 * <p>Covers: R11.1, R11.2。
 */
@Component
public class CheckstyleScanner extends AbstractStaticScanner {

    public static final String NAME = "checkstyle";

    public CheckstyleScanner(ScannerConfigMapper scannerConfigMapper,
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
        return Set.of("java");
    }

    @Override
    protected boolean fileMatches(String filePath) {
        return filePath != null && filePath.toLowerCase().endsWith(".java");
    }
}
