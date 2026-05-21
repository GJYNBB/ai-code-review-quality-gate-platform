package com.acrqg.platform.webhook.controller;

import com.acrqg.platform.common.api.ApiResponse;
import com.acrqg.platform.common.api.ErrorCode;
import com.acrqg.platform.common.exception.BusinessException;
import com.acrqg.platform.repository.domain.Provider;
import com.acrqg.platform.webhook.dto.WebhookHandleResult;
import com.acrqg.platform.webhook.service.WebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.Enumeration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Webhook 入口控制器（B3-B.4 / R7）。
 *
 * <p>对齐 design.md §8.7：
 * <pre>
 * POST /api/v1/webhooks/git    签名校验（不需 Bearer）
 * </pre>
 *
 * <p>白名单已在 {@code SecurityConfig.PERMIT_ALL} / {@code JwtAuthFilter.WHITELIST}
 * 中以 {@code /api/v1/webhooks/**} 形式开放，不需要登录即可调用。
 *
 * <h3>Provider 推断</h3>
 * <ul>
 *   <li>{@code X-GitHub-Event} 头存在 → {@link Provider#GITHUB}</li>
 *   <li>{@code X-Gitlab-Event} 头存在 → {@link Provider#GITLAB}</li>
 *   <li>{@code X-Gitee-Event} 头存在 → {@link Provider#GITEE}</li>
 *   <li>三个头都不存在 → 抛 {@link ErrorCode#VALIDATION_ERROR}</li>
 * </ul>
 *
 * <h3>原始 body 处理</h3>
 * <p>{@link RequestBody String rawBody} 让 Spring 把请求体作为 UTF-8 字符串注入。
 * GitHub 在计算 HMAC-SHA256 时使用客户端发送的字节序列，因此 Spring 不得对 body
 * 做任何 deserialize / serialize；本控制器声明 {@code consumes = "*/*"}，
 * 让 {@code StringHttpMessageConverter} 直接读取原始字节并以 UTF-8 解码。
 *
 * <h3>错误处理</h3>
 * <p>所有 {@link BusinessException} 由 {@code GlobalExceptionHandler} 统一映射为
 * {@link ApiResponse}；本控制器不需要捕获异常自行转换。
 *
 * <p>Covers: R7.1, R7.6, R23.1。
 */
@RestController
@RequestMapping("/api/v1/webhooks")
@Tag(name = "Webhook", description = "代码托管平台 webhook 入口（M03 / R7）")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    /** GitHub 事件类型头。 */
    private static final String HEADER_GITHUB_EVENT = "X-GitHub-Event";
    /** GitLab 事件类型头。 */
    private static final String HEADER_GITLAB_EVENT = "X-Gitlab-Event";
    /** Gitee 事件类型头。 */
    private static final String HEADER_GITEE_EVENT = "X-Gitee-Event";

    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @Operation(summary = "Git 平台 Webhook 入口",
            description = "接收 GitHub / GitLab / Gitee 平台的 webhook。"
                    + " 必须带 X-GitHub-Event / X-Gitlab-Event / X-Gitee-Event 之一识别 provider。"
                    + " GitHub 校验 X-Hub-Signature-256；GitLab 校验 X-Gitlab-Token；"
                    + " Gitee 校验 X-Gitee-Token。"
                    + " PING / 不支持的事件类型返回 ignored=true，不创建任务。"
                    + " 同一 (provider, repositoryId, eventId) 24 小时内重复送达返回同一 taskId。"
                    + " 全流程必须在 3 秒内返回（R7.6），耗时操作由 Stream 异步消费。")
    @PostMapping(value = "/git", consumes = MediaType.ALL_VALUE)
    public ApiResponse<WebhookHandleResult> receive(
            @RequestBody(required = false) String rawBody,
            HttpServletRequest request) {
        HttpHeaders headers = collectHeaders(request);
        Provider provider = detectProvider(headers);
        if (log.isDebugEnabled()) {
            log.debug("webhook received: provider={} contentLength={}",
                    provider, rawBody == null ? 0 : rawBody.length());
        }
        WebhookHandleResult result = webhookService.handle(provider, headers, rawBody);
        return ApiResponse.success(result);
    }

    /**
     * 把 servlet 请求头收敛为 {@link HttpHeaders}（保留全部原始头，大小写不敏感）。
     */
    private static HttpHeaders collectHeaders(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        Enumeration<String> names = request.getHeaderNames();
        if (names == null) {
            return headers;
        }
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            Enumeration<String> values = request.getHeaders(name);
            if (values == null) {
                continue;
            }
            while (values.hasMoreElements()) {
                headers.add(name, values.nextElement());
            }
        }
        // HttpHeaders 自身按 ASCII 不敏感方式查询，无需转小写
        return headers;
    }

    /**
     * 根据请求头推断 {@link Provider}。
     *
     * <p>优先级：GitHub > GitLab > Gitee（按事件头存在性）。三个头都缺失抛
     * {@link ErrorCode#VALIDATION_ERROR}，因为这表示请求不是合法的 Git 平台 webhook。
     */
    private static Provider detectProvider(HttpHeaders headers) {
        if (headers == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "headers missing");
        }
        if (hasHeader(headers, HEADER_GITHUB_EVENT)) {
            return Provider.GITHUB;
        }
        if (hasHeader(headers, HEADER_GITLAB_EVENT)) {
            return Provider.GITLAB;
        }
        if (hasHeader(headers, HEADER_GITEE_EVENT)) {
            return Provider.GITEE;
        }
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "unsupported provider");
    }

    private static boolean hasHeader(HttpHeaders headers, String name) {
        return !Collections.unmodifiableList(
                headers.getOrDefault(name, Collections.emptyList())).isEmpty();
    }
}
