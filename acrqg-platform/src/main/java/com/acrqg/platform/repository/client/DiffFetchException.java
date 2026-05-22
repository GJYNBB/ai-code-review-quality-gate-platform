package com.acrqg.platform.repository.client;

import com.acrqg.platform.repository.domain.Provider;

/**
 * Diff 拉取异常（design.md §6.4 / R10.4）。
 *
 * <p>由 {@link ProviderClient#fetchDiff} 在网络异常 / 4xx / 5xx 等不可恢复
 * 失败时抛出，由 {@code DiffParser} 捕获并通过 {@code TaskLogger.error}
 * 记录到 {@code task_log}，再向上抛出由 {@code TaskOrchestrator} 转
 * EXECUTION_FAILED（R9.2）。
 *
 * <p>异常 message 中包含 provider / repoUrl / prId 三元组，便于运维排查；
 * 实现 <b>不得</b> 把 access token 写入 message。
 *
 * <p>Covers: R10.4, R23.3。
 */
public class DiffFetchException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final Provider provider;
    private final String repoUrl;
    private final String prId;

    public DiffFetchException(Provider provider, String repoUrl, String prId, String message) {
        super(format(provider, repoUrl, prId, message));
        this.provider = provider;
        this.repoUrl = repoUrl;
        this.prId = prId;
    }

    public DiffFetchException(Provider provider, String repoUrl, String prId,
                              String message, Throwable cause) {
        super(format(provider, repoUrl, prId, message), cause);
        this.provider = provider;
        this.repoUrl = repoUrl;
        this.prId = prId;
    }

    public Provider getProvider() {
        return provider;
    }

    public String getRepoUrl() {
        return repoUrl;
    }

    public String getPrId() {
        return prId;
    }

    private static String format(Provider provider, String repoUrl, String prId, String message) {
        return "[provider=" + (provider == null ? "?" : provider.name())
                + ", repoUrl=" + (repoUrl == null ? "?" : repoUrl)
                + ", prId=" + (prId == null ? "?" : prId)
                + "] " + (message == null ? "diff fetch failed" : message);
    }
}
