package com.acrqg.platform.scanner.adapter;

import com.acrqg.platform.admin.repository.ScannerConfigMapper;
import com.acrqg.platform.scanner.parser.ScanResultParserRegistry;
import com.acrqg.platform.scanner.process.ScannerProcessRunner;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * ESLint 适配器（JavaScript / TypeScript）。
 *
 * <p>仅扫描 *.js / *.jsx / *.ts / *.tsx / *.mjs / *.cjs 文件。命令模板执行后输出
 * JSON 由 {@link com.acrqg.platform.scanner.parser.EsLintJsonParser} 解析。
 *
 * <p>Covers: R11.1, R11.2。
 */
@Component
public class EsLintScanner extends AbstractStaticScanner {

    public static final String NAME = "eslint";

    public EsLintScanner(ScannerConfigMapper scannerConfigMapper,
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
        return Set.of("javascript", "typescript");
    }

    @Override
    protected boolean fileMatches(String filePath) {
        if (filePath == null) {
            return false;
        }
        String lower = filePath.toLowerCase();
        return lower.endsWith(".js") || lower.endsWith(".jsx")
                || lower.endsWith(".ts") || lower.endsWith(".tsx")
                || lower.endsWith(".mjs") || lower.endsWith(".cjs");
    }
}
