package com.acrqg.platform.webhook.verifier;

import com.acrqg.platform.repository.domain.Provider;
import org.springframework.http.HttpHeaders;

/**
 * Webhook 签名校验器（B3-B.1）。
 *
 * <p>三种代码托管平台（GitHub / GitLab / Gitee）下发 webhook 时使用的签名 / 共享
 * token 风格不一致：
 * <ul>
 *   <li>GitHub —— 头 {@code X-Hub-Signature-256}，内容是
 *       {@code "sha256=" + HMAC-SHA256(body, secret).hex()}（HMAC + 大小写不敏感的
 *       小写十六进制）。</li>
 *   <li>GitLab —— 头 {@code X-Gitlab-Token}，内容是直接配置的明文 token，
 *       与 secret 直接相等比较即可。</li>
 *   <li>Gitee  —— 头 {@code X-Gitee-Token}，与 GitLab 风格一致（Gitee 也支持 HMAC
 *       但本平台与 GitLab 对齐使用明文 token）。</li>
 * </ul>
 *
 * <p>所有实现必须使用 {@link java.security.MessageDigest#isEqual(byte[], byte[])}
 * 做恒定时间字节比较（R23.3 / design.md §13.3），抵御计时侧信道攻击。
 *
 * <p>头部缺失、为空或不匹配统一返回 {@code false}；不要抛异常，由上层
 * {@code WebhookService} 把布尔值转为 {@code WEBHOOK_SIGNATURE_INVALID}。
 *
 * <p>Covers: R7.1, R7.2, R23.3。
 */
public interface SignatureVerifier {

    /**
     * 校验单次 webhook 请求的签名 / 共享 token。
     *
     * @param secret  绑定时配置的密钥（明文；GitHub 用作 HMAC key，GitLab/Gitee 用作
     *                直接相等比较的 token）。{@code null} 视为校验失败。
     * @param body    原始 HTTP 请求体（字节级别保留，因 GitHub 计算 HMAC 必须基于
     *                客户端发送的字节序列；如果服务端先 deserialize 再 serialize 会
     *                因键顺序 / 空白差异导致 HMAC 不一致）。{@code null} 视为校验失败。
     * @param headers 请求头集合（大小写不敏感）。{@code null} 视为校验失败。
     * @return 当且仅当头部存在且签名一致时返回 {@code true}；其他情况返回 {@code false}
     */
    boolean verify(String secret, String body, HttpHeaders headers);

    /**
     * 该实现负责的代码托管平台。{@link SignatureVerifierFactory} 在
     * {@code @PostConstruct} 阶段按此值建立 {@code Provider -> Verifier} 映射。
     */
    Provider provider();
}
