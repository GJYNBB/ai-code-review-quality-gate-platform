package com.acrqg.platform.scanner.parser;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 结果解析器注册表。
 *
 * <p>Spring 启动时把所有 {@link ScanResultParser} bean 注入进来，按 {@link ScanResultParser#type()}
 * 建立映射。{@link com.acrqg.platform.scanner.adapter.StaticScannerAdapter} 通过
 * {@code scanner_config.result_parser_type} 路由到对应解析器。
 *
 * <p>Covers: R11.2。
 */
@Component
public class ScanResultParserRegistry {

    private final Map<String, ScanResultParser> byType;

    public ScanResultParserRegistry(List<ScanResultParser> parsers) {
        Map<String, ScanResultParser> m = new LinkedHashMap<>();
        for (ScanResultParser p : parsers) {
            String type = p.type();
            if (type == null || type.isBlank()) {
                continue;
            }
            m.put(type, p);
        }
        this.byType = Map.copyOf(m);
    }

    /** 按 {@code scanner_config.result_parser_type} 取解析器；缺失返回 {@code null}。 */
    public ScanResultParser get(String type) {
        if (type == null) {
            return null;
        }
        return byType.get(type);
    }
}
