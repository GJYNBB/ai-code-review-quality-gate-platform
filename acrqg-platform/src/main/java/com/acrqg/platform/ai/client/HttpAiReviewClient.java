package com.acrqg.platform.ai.client;

import com.acrqg.platform.ai.exception.AiServiceUnavailableException;
import com.acrqg.platform.ai.schema.AiReviewSchemaValidator;
import com.acrqg.platform.common.api.ErrorCode;
import com.acrqg.platform.common.exception.BusinessException;
import com.acrqg.platform.infra.net.OutboundUrlGuard;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * {@link AiReviewClient} 的 HTTP 实现（design.md §6.6 / §12.4）。
 *
 * <p>面向 OpenAI 兼容的 chat completions 接口：
 * <pre>
 * POST {baseUrl}/v1/chat/completions
 * Authorization: Bearer {apiKey}
 * Content-Type: application/json
 * Body: { "model": "{modelName}",
 *         "messages": [
 *           {"role":"system","content":"{systemPrompt}"},
 *           {"role":"user",  "content":"{userPrompt}"}
 *         ],
 *         "temperature": 0,
 *         "response_format": {"type":"json_object"} }
 * </pre>
 *
 * <h3>异常映射（与 design §12.4 一致）</h3>
 * <ul>
 *   <li>{@link ResourceAccessException}（含 {@code SocketTimeoutException} /
 *       {@code UnknownHostException}）→ {@link AiServiceUnavailableException}；</li>
 *   <li>{@link HttpServerErrorException} 5xx → {@link AiServiceUnavailableException}；</li>
 *   <li>{@link HttpClientErrorException} 4xx → {@link BusinessException}
 *       （{@link ErrorCode#VALIDATION_ERROR}），不进入降级路径。</li>
 * </ul>
 *
 * <p>响应内容为 JSON object，{@code choices[0].message.content} 即模型生成的
 * JSON 文本（已通过 {@code response_format=json_object} 强约束）。本客户端解析后
 * 喂入 {@link AiReviewSchemaValidator}，校验失败时抛
 * {@link com.acrqg.platform.ai.exception.SchemaValidationException}。
 *
 * <h3>线程安全</h3>
 * <p>底层 {@link RestClient} 线程安全；本类不持有可变状态。每次调用根据
 * {@link AiReviewRequest#timeoutSeconds()} 重新构造 {@link ClientHttpRequestFactory}，
 * 避免不同模型 / 不同任务的超时互相影响。
 *
 * <p>Covers: R12.1, R12.3, R12.4, R12.5。
 */
@Component
public class HttpAiReviewClient implements AiReviewClient {

    private static final Logger log = LoggerFactory.getLogger(HttpAiReviewClient.class);

    /** 连接超时（毫秒）。读超时由请求级别参数 timeoutSeconds 控制。 */
    static final int CONNECT_TIMEOUT_MILLIS = 10_000;

    private final ObjectMapper objectMapper;
    private final AiReviewSchemaValidator schemaValidator;

    public HttpAiReviewClient(ObjectMapper objectMapper,
                              AiReviewSchemaValidator schemaValidator) {
        this.objectMapper = objectMapper;
        this.schemaValidator = schemaValidator;
    }

    @Override
    public AiReviewResponse review(AiReviewRequest req, String apiKey) {
        if (req == null) {
            throw new IllegalArgumentException("req is required");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey is required");
        }
        if (req.baseUrl() == null || req.baseUrl().isBlank()) {
            throw new IllegalArgumentException("baseUrl is required");
        }

        RestClient client = buildClient(req.timeoutSeconds());
        String baseUrl;
        try {
            baseUrl = stripTrailingSlash(OutboundUrlGuard.requireHttpsPublicUrl(
                    req.baseUrl(), "AI model baseUrl").toString());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, ex.getMessage(), ex);
        }
        String url = baseUrl + "/v1/chat/completions";
        Map<String, Object> body = buildBody(req);

        String responseText;
        try {
            responseText = client.post()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (HttpClientErrorException e4xx) {
            // 4xx：不可恢复（鉴权 / 参数）→ 业务异常
            log.warn("AI client 4xx: status={} reason={}",
                    e4xx.getStatusCode(), simpleMessage(e4xx));
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "AI 服务返回 " + e4xx.getStatusCode().value()
                            + "，请检查模型配置（apiKey/baseUrl）");
        } catch (HttpServerErrorException e5xx) {
            log.warn("AI client 5xx: status={} reason={}",
                    e5xx.getStatusCode(), simpleMessage(e5xx));
            throw new AiServiceUnavailableException(
                    "AI service 5xx: " + e5xx.getStatusCode().value(), e5xx);
        } catch (ResourceAccessException ex) {
            // 网络层异常（连接 / 读超时 / 未知主机）
            log.warn("AI client network error: {}", simpleMessage(ex));
            throw new AiServiceUnavailableException(classifyNetworkError(ex), ex);
        }

        if (responseText == null || responseText.isBlank()) {
            throw new AiServiceUnavailableException("AI returned empty body");
        }

        // 解析 OpenAI 包装层，抽取 message.content
        String content = extractContent(responseText);

        // content 是 AI 模型生成的 JSON 字符串；交给 schema validator 校验
        JsonNode root;
        try {
            root = objectMapper.readTree(content);
        } catch (Exception e) {
            // 顶层 JSON 不合法 → 视为 schema 校验失败（不可降级到 BusinessException，
            // 因为我们要让 AiReviewService 把它当作"WARN + 丢弃响应"处理 R12.4）
            throw new com.acrqg.platform.ai.exception.SchemaValidationException(
                    "AI response is not valid JSON: " + simpleMessage(e),
                    List.of("$: not parseable JSON"));
        }
        return schemaValidator.validate(root);
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    private static RestClient buildClient(int timeoutSeconds) {
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Duration.ofMillis(CONNECT_TIMEOUT_MILLIS));
        // 读超时直接使用请求级 timeoutSeconds（兜底 10s 上限保护）
        int readMs = Math.max(timeoutSeconds, 1) * 1_000;
        rf.setReadTimeout(Duration.ofMillis(readMs));
        return RestClient.builder().requestFactory(rf).build();
    }

    private static Map<String, Object> buildBody(AiReviewRequest req) {
        Map<String, Object> sys = new LinkedHashMap<>();
        sys.put("role", "system");
        sys.put("content", req.systemPrompt() == null ? "" : req.systemPrompt());
        Map<String, Object> usr = new LinkedHashMap<>();
        usr.put("role", "user");
        usr.put("content", req.userPrompt() == null ? "" : req.userPrompt());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", req.modelName() == null ? "gpt-4o-mini" : req.modelName());
        body.put("messages", List.of(sys, usr));
        body.put("temperature", 0);
        // OpenAI / 兼容网关支持的 JSON object 强约束
        body.put("response_format", Map.of("type", "json_object"));
        return body;
    }

    /**
     * 从 OpenAI 包装层抽取 {@code choices[0].message.content}。
     * 当字段缺失时直接把整个响应当作 JSON 内容（兼容部分非完全 OpenAI 兼容的网关）。
     */
    String extractContent(String responseText) {
        try {
            JsonNode root = objectMapper.readTree(responseText);
            JsonNode choices = root.get("choices");
            if (choices != null && choices.isArray() && choices.size() > 0) {
                JsonNode msg = choices.get(0).get("message");
                if (msg != null) {
                    JsonNode content = msg.get("content");
                    if (content != null && content.isTextual()) {
                        return content.asText();
                    }
                }
            }
            // 网关直接返回 issues / summary 形态时，整体即视为内容
            if (root.isObject() && root.has("issues")) {
                return responseText;
            }
        } catch (Exception ignore) {
            // 整体解析失败时回退为按"内容"处理；schema validator 会再报错
        }
        return responseText;
    }

    private static String classifyNetworkError(ResourceAccessException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof SocketTimeoutException) {
            return "AI service timeout";
        }
        if (cause instanceof UnknownHostException) {
            return "AI service unknown host";
        }
        return "AI service network error: " + simpleMessage(ex);
    }

    private static String simpleMessage(Throwable t) {
        String m = t.getMessage();
        if (m == null || m.isBlank()) {
            return t.getClass().getSimpleName();
        }
        return m;
    }

    private static String stripTrailingSlash(String url) {
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }
}
