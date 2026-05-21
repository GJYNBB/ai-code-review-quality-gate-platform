package com.acrqg.platform.writeback.exception;

import com.acrqg.platform.repository.domain.Provider;

/**
 * Writeback 操作异常（design.md §6.9 / R20.3 / R20.4）。
 *
 * <p>由 {@link com.acrqg.platform.repository.client.ProviderClient#postCommitStatus
 * ProviderClient.postCommitStatus} 在网络异常 / 4xx / 5xx 等不可恢复失败时抛出，
 * 由 {@code WritebackService} 捕获并按重试策略决定后续动作（4xx 不重试 / 5xx 指数退避）。
 *
 * <p>异常 message 中包含 provider / repoUrl / commitSha 三元组，便于运维排查；
 * 实现 <b>不得</b> 把 access token 写入 message（R23.3）。
 *
 * <p>{@link #httpStatus} 字段记录上游响应的 HTTP 状态码（网络异常时为 0），
 * 供调用方区分"4xx 不重试"与"5xx 重试"。
 *
 * <p>Covers: R20.3, R20.4, R23.3。
 */
public class WritebackException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final Provider provider;
    private final String commitSha;
    private final int httpStatus;

    public WritebackException(Provider provider, String commitSha, int httpStatus,
                              String message) {
        super(format(provider, commitSha, message));
        this.provider = provider;
        this.commitSha = commitSha;
        this.httpStatus = httpStatus;
    }

    public WritebackException(Provider provider, String commitSha, int httpStatus,
                              String message, Throwable cause) {
        super(format(provider, commitSha, message), cause);
        this.provider = provider;
        this.commitSha = commitSha;
        this.httpStatus = httpStatus;
    }

    public Provider getProvider() {
        return provider;
    }

    public String getCommitSha() {
        return commitSha;
    }

    /** 上游 HTTP 状态码（网络异常时为 0）。 */
    public int getHttpStatus() {
        return httpStatus;
    }

    /** 是否属于客户端错误（4xx），用于判定"不重试"。 */
    public boolean isClientError() {
        return httpStatus >= 400 && httpStatus < 500;
    }

    /** 是否属于服务端错误（5xx）或网络异常（0），用于判定"可重试"。 */
    public boolean isRetryable() {
        return httpStatus == 0 || httpStatus >= 500;
    }

    private static String format(Provider provider, String commitSha, String message) {
        return "[provider=" + (provider == null ? "?" : provider.name())
                + ", commitSha=" + (commitSha == null ? "?" : commitSha)
                + "] " + (message == null ? "writeback failed" : message);
    }
}
