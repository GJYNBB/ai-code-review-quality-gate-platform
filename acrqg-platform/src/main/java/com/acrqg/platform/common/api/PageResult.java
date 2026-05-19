package com.acrqg.platform.common.api;

import java.util.List;

/**
 * 分页查询的统一结果包装。
 *
 * <p>对应 design.md §8.1 的 record 定义。所有 {@code GET /api/v1/.../pages} 风格的
 * 列表接口均通过 {@code ApiResponse<PageResult<T>>} 返回。
 *
 * <p>JSON 示例（{@code GET /api/v1/users?page=1&pageSize=20}）：
 * <pre>
 * {
 *   "code": 0,
 *   "message": "success",
 *   "data": {
 *     "items": [ { "id": 1, "username": "alice" }, ... ],
 *     "page": 1,
 *     "pageSize": 20,
 *     "total": 137,
 *     "totalPages": 7
 *   },
 *   "requestId": "550e8400-..."
 * }
 * </pre>
 *
 * <p>{@code totalPages} 由 {@link #of(List, int, int, long)} 工厂方法在构造时一次性计算，
 * 公式为 {@code ceil(total / pageSize)}；当 {@code pageSize <= 0} 时（理论上不会发生，但
 * 控制器层 Bean Validation 仍可能漏掉）退化为 {@code 0}，避免运行时除零异常。
 *
 * <p>Covers: R3.1, R16.2, R16.5, R19.3, R22.2.
 *
 * @param <T>        列表元素类型
 * @param items      当前页元素，非 {@code null}（空页应使用 {@link List#of()}）
 * @param page       当前页码，从 1 起
 * @param pageSize   每页条数
 * @param total      总条数
 * @param totalPages 总页数 = {@code ceil(total / pageSize)}；{@code pageSize<=0} 时为 0
 */
public record PageResult<T>(
        List<T> items,
        int page,
        int pageSize,
        long total,
        int totalPages
) {

    /**
     * 构造分页结果。{@code totalPages} 自动计算。
     *
     * @param items    当前页元素
     * @param page     当前页码
     * @param pageSize 每页条数
     * @param total    总条数
     * @param <T>      列表元素类型
     * @return 分页结果包装
     */
    public static <T> PageResult<T> of(List<T> items, int page, int pageSize, long total) {
        int totalPages;
        if (pageSize <= 0) {
            // defensive: 控制器层 Bean Validation 一般会拦下 pageSize<=0；此处兜底防止 / 0
            totalPages = 0;
        } else {
            totalPages = (int) Math.ceil(total / (double) pageSize);
        }
        return new PageResult<>(items, page, pageSize, total, totalPages);
    }
}
