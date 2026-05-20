package com.acrqg.platform.admin.dto;

import java.time.OffsetDateTime;

/**
 * 扫描器配置对外视图。
 *
 * <p>由 {@code AdminService.upsertScanner} / {@code listScanners} /
 * {@code getScanner} 返回，通过 {@code ApiResponse<ScannerConfigDTO>} 或
 * {@code ApiResponse<List<ScannerConfigDTO>>} 包装。
 *
 * <p>Covers: R21.3。
 *
 * @param id               主键
 * @param name             扫描器名称
 * @param language         适用语言
 * @param enabled          是否启用
 * @param command          命令模板
 * @param resultParserType 结果解析类型
 * @param createdAt        创建时间
 * @param updatedAt        最近更新时间
 */
public record ScannerConfigDTO(
        Long id,
        String name,
        String language,
        boolean enabled,
        String command,
        String resultParserType,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
