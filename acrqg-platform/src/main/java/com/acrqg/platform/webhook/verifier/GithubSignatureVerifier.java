package com.acrqg.platform.webhook.verifier;

import com.acrqg.platform.repository.domain.Provider;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/**
 * GitHub Webhook 签名校验器（B3-B.1）。
 *
 * <p>GitHub 在请求头 {@code X-Hub-Signature-256} 中下发签名，格式为：
 * <pre>
 * X-Hub-Signature-256: sha256=&lt;hex(HMAC-SHA256(body, secret))&gt;
 * </pre>
 *
 * <p>其中 hex 为小写十六进制串。本实现：
 * <ol>
 *   <li>从头中提取签名串；缺失或前缀不为 {@code "sha256="} 视为失败；</li>
 *   <li>用 {@code Mac("HmacSHA256")} 配合 {@code secret} 字节计算 body 的 MAC；</li>
 *   <li>拼接 {@code "sha256=" + hex(mac)} 与上游签名做
 *       {@link MessageDigest#isEqual} 恒定时间比较。</li>
 * </ol>
 *
 * <p>GitHub 文档：
 * <a href="https://docs.github.com/en/webhooks/using-webhooks/validating-webhook-deliveries">
 * Validating webhook deliveries</a>。
 *
 * <p>Covers: R7.1, R7.2, R23.3。
 */
@Component
public class GithubSignatureVerifier implements SignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(GithubSignatureVerifier.class);

    /** GitHub 签名头名。 */
    public static final String HEADER_SIGNATURE_256 = "X-Hub-Signature-256";

    /** Mac 算法名。 */
    private static final String HMAC_SHA_256 = "HmacSHA256";

    /** 期望前缀。 */
    private static final String PREFIX = "sha256=";

    @Override
    public boolean verify(String secret, String body, HttpHeaders headers) {
        return verify(secret, body == null ? null : body.getBytes(StandardCharsets.UTF_8), headers);
    }

    @Override
    public boolean verify(String secret, byte[] body, HttpHeaders headers) {
        if (secret == null || body == null || headers == null) {
            return false;
        }
        String signature = headers.getFirst(HEADER_SIGNATURE_256);
        if (signature == null || signature.isBlank() || !signature.startsWith(PREFIX)) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance(HMAC_SHA_256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA_256));
            byte[] digest = mac.doFinal(body);
            String expected = PREFIX + HexFormat.of().formatHex(digest);
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            // JCE 应当总是支持 HmacSHA256；遇到此分支视为环境配置错误，记日志后返回失败
            log.error("HmacSHA256 not available or secret invalid: {}", e.toString());
            return false;
        }
    }

    @Override
    public Provider provider() {
        return Provider.GITHUB;
    }
}
