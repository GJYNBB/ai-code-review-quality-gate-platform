package com.acrqg.platform.common.api;

/**
 * 字段级错误明细。
 *
 * <p>用于 {@link ApiResponse#details()} 字段，承载 Bean Validation 失败时
 * 每一个不合法字段的诊断信息（字段路径 + 原因），由前端按字段展示提示。
 *
 * <p>对应 design.md §8.1 中的定义：
 * <pre>
 * public record FieldError(String field, String reason) {}
 * </pre>
 *
 * <p>Covers: R3.3, R4.3, R6.2, R8.2, R13.3, R15.2, R17.2, R18.2, R21.4
 *
 * @param field  字段路径，例如 {@code "members[0].userId"} 或 {@code "name"}
 * @param reason 原因描述，使用人类可读的中文短句
 */
public record FieldError(String field, String reason) {
}
