package com.acrqg.platform.webhook.verifier;

import com.acrqg.platform.repository.domain.Provider;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/**
 * Gitee Webhook 签名校验器（B3-B.1）。
 *
 * <p>Gitee 在请求头 {@code X-Gitee-Token} 中下发与绑定时配置完全相同的明文 token。
 * 与 GitLab 风格一致：直接与 secret 做恒定时间比较即可。
 *
 * <p>Gitee 也支持基于 HMAC-SHA256 的 {@code X-Gitee-Token}（"密码方式"），但本平台
 * 在绑定环节统一收集明文 token，因此 Verifier 端按"密码方式"实现以与
 * Gitee 默认行为对齐。
 *
 * <p>Gitee 文档：
 * <a href="https://gitee.com/help/articles/4290">Webhook 介绍</a>。
 *
 * <p>Covers: R7.1, R7.2, R23.3。
 */
@Component
public class GiteeSignatureVerifier implements SignatureVerifier {

    /** Gitee token 头名。 */
    public static final String HEADER_TOKEN = "X-Gitee-Token";

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
        return Provider.GITEE;
    }
}
