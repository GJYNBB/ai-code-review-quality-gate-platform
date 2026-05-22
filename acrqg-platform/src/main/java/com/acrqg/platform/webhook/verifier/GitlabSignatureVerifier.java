package com.acrqg.platform.webhook.verifier;

import com.acrqg.platform.repository.domain.Provider;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/**
 * GitLab Webhook 签名校验器（B3-B.1）。
 *
 * <p>GitLab 在请求头 {@code X-Gitlab-Token} 中下发与绑定时配置完全相同的明文 token；
 * 直接与 secret 做恒定时间比较即可（design.md §13.3 原文："GitLab 使用
 * X-Gitlab-Token，由 provider 字段路由到不同 Verifier"）。
 *
 * <p>GitLab 文档：
 * <a href="https://docs.gitlab.com/ee/user/project/integrations/webhook_events.html">
 * Webhook events</a>。
 *
 * <p>Covers: R7.1, R7.2, R23.3。
 */
@Component
public class GitlabSignatureVerifier implements SignatureVerifier {

    /** GitLab token 头名。 */
    public static final String HEADER_TOKEN = "X-Gitlab-Token";

    @Override
    public boolean verify(String secret, String body, HttpHeaders headers) {
        if (secret == null || headers == null) {
            return false;
        }
        String token = headers.getFirst(HEADER_TOKEN);
        if (token == null || token.isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(
                secret.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Provider provider() {
        return Provider.GITLAB;
    }
}
