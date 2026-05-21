package com.acrqg.platform.scanner.parser;

/**
 * {@code scanner_config.result_parser_type} 字面量集合。
 *
 * <p>避免在多处出现魔法字符串。值与 V12__m10_admin.sql 种子数据一致。
 */
public final class ResultParserType {

    public static final String CHECKSTYLE_XML = "CHECKSTYLE_XML";
    public static final String ESLINT_JSON = "ESLINT_JSON";
    public static final String PYLINT_JSON = "PYLINT_JSON";
    public static final String SEMGREP_JSON = "SEMGREP_JSON";

    private ResultParserType() {
        // utility
    }
}
