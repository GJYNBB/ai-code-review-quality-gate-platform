package com.acrqg.platform.repository.dto;

/**
 * 连通性测试结果（design.md §8.4 / R5.1）。
 *
 * <p>由 {@code RepositoryService.test} 与 {@code ProviderClient.ping} 返回。
 *
 * <ul>
 *   <li>{@code reachable}：仓库平台是否可访问（HTTP 2xx 视为可达）；</li>
 *   <li>{@code message}：人类可读的状态提示。可达时建议 {@code "OK"}；
 *       不可达时按错误类型提供：
 *       <ul>
 *         <li>{@code "invalid token"}（401 / 403）；</li>
 *         <li>{@code "repo not found"}（404）；</li>
 *         <li>底层网络异常时回填 {@code Throwable.getMessage()}（不含敏感字段）。</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * <p>Covers: R5.1, R5.2。
 *
 * @param reachable 仓库是否可达
 * @param message   状态描述（不含 token / secret）
 */
public record ConnectivityResultDTO(boolean reachable, String message) {

    /** 便捷工厂：可达 + "OK"。 */
    public static ConnectivityResultDTO ok() {
        return new ConnectivityResultDTO(true, "OK");
    }

    /** 便捷工厂：不可达 + 指定原因。 */
    public static ConnectivityResultDTO unreachable(String message) {
        return new ConnectivityResultDTO(false, message == null ? "unreachable" : message);
    }
}
