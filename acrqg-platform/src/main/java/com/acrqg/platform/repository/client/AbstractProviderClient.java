package com.acrqg.platform.repository.client;

import com.acrqg.platform.infra.net.GuardedRestClientFactory;
import com.acrqg.platform.repository.dto.ConnectivityResultDTO;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * {@link ProviderClient} 实现的共用基类。
 *
 * <p>核心职责：
 * <ul>
 *   <li>构造统一的 {@link RestClient}，强制 {@code connect=10s / read=10s} 超时
 *       （R5.1 / R24.4）；</li>
 *   <li>把任意 HTTP / 网络异常映射为 {@link ConnectivityResultDTO}，避免子类
 *       重复编写 try-catch 模板代码；</li>
 *   <li>给 {@link ProviderClient#postCommitStatus} 提供"未实现"占位（B4-E 完成）。</li>
 * </ul>
 *
 * <p>{@link ProviderClient#fetchDiff} 在 B3-C.3 由各子类提供完整实现；
 * 本基类不再保留占位。
 *
 * <p>Covers: R5.1, R5.2, R10.1, R20.1。
 */
abstract class AbstractProviderClient implements ProviderClient {

    /** 连接 / 读取超时（毫秒）。10s 与 design.md §11 保持一致。 */
    static final int TIMEOUT_MILLIS = 10_000;

    /** {@link #fetchDiff} 单次调用允许的最大分页数；超过即停止并 WARN（防御性兜底）。 */
    static final int MAX_PAGES = 30;

    /** 子类共享的 logger；按子类的 logger name 输出，方便日志检索。 */
    protected final Logger log = LoggerFactory.getLogger(getClass());

    /** 共享 RestClient 实例（线程安全）。 */
    protected final RestClient restClient;

    AbstractProviderClient() {
        this.restClient = buildRestClient();
    }

    /** 构造带 10s 超时、禁用重定向并在连接路径执行 DNS 防护的 RestClient。 */
    private static RestClient buildRestClient() {
        return GuardedRestClientFactory.build(
                Duration.ofMillis(TIMEOUT_MILLIS),
                Duration.ofMillis(TIMEOUT_MILLIS));
    }

    /**
     * 把 HTTP 异常 / 网络异常归一化为 {@link ConnectivityResultDTO}。
     *
     * <p>调用 {@link #ping} 子类实现的核心 HTTP 调用；不抛异常，永远返回 DTO。
     *
     * @param call 子类提供的"原子调用"，命中 2xx 时被认为可达
     * @return 归一化后的连通性结果
     */
    protected ConnectivityResultDTO runPing(Runnable call) {
        try {
            call.run();
            return ConnectivityResultDTO.ok();
        } catch (HttpClientErrorException ex) {
            return mapClientError(ex.getStatusCode());
        } catch (HttpServerErrorException ex) {
            int status = ex.getStatusCode().value();
            return ConnectivityResultDTO.unreachable("unreachable: HTTP " + status);
        } catch (ResourceAccessException ex) {
            return mapResourceAccessError(ex);
        } catch (RuntimeException ex) {
            log.warn("provider ping failed: {}", ex.toString());
            return ConnectivityResultDTO.unreachable("unreachable: " + simpleMessage(ex));
        }
    }

    private static ConnectivityResultDTO mapClientError(HttpStatusCode status) {
        int code = status.value();
        if (code == 401 || code == 403) {
            return ConnectivityResultDTO.unreachable("invalid token");
        }
        if (code == 404) {
            return ConnectivityResultDTO.unreachable("repo not found");
        }
        return ConnectivityResultDTO.unreachable("unreachable: HTTP " + code);
    }

    private static ConnectivityResultDTO mapResourceAccessError(ResourceAccessException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof SocketTimeoutException) {
            return ConnectivityResultDTO.unreachable("unreachable: timeout");
        }
        if (cause instanceof UnknownHostException) {
            return ConnectivityResultDTO.unreachable("unreachable: unknown host");
        }
        return ConnectivityResultDTO.unreachable("unreachable: " + simpleMessage(ex));
    }

    private static String simpleMessage(Throwable t) {
        String msg = t.getMessage();
        if (msg == null || msg.isBlank()) {
            return t.getClass().getSimpleName();
        }
        return msg;
    }
}
