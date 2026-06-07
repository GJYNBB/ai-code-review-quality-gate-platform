package com.acrqg.platform.infra.crypto;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 应用层 Token 加密器：{@link AesGcmCipher} 的 Spring 薄包装。
 *
 * <p>由 {@code app.security.token-encryption-key} 配置注入主口令（来源于
 * {@code application.yml} / 环境变量 {@code TOKEN_ENCRYPTION_KEY}），
 * 在 {@link #init()} 阶段构造一次 {@link AesGcmCipher} 实例后复用。
 *
 * <p>本类是其他模块（B2-A 仓库绑定 access_token / webhook_secret、B1-D 模型
 * api_key、B3-* 任务参数等）加解密的唯一入口；业务代码不应直接持有
 * {@link AesGcmCipher}，以便后续统一切换密钥派生策略或加入轮换逻辑。
 *
 * <p>启动期失败语义：当 passphrase 缺失、过短或 JCE 不可用时，
 * {@link AesGcmCipher} 构造方法会抛 {@link IllegalArgumentException} /
 * {@link IllegalStateException}，{@link PostConstruct} 将以异常方式
 * 中止 Spring 上下文启动，便于尽早暴露配置问题。
 *
 * <p>Covers: R5.3 (accessToken / webhookSecret 加密存储), R21.1
 * (模型 apiKey 加密存储), R23.2 (对称加密 + 主密钥来自系统参数)。
 */
@Component
public class TokenEncryptor {

    private static final Logger log = LoggerFactory.getLogger(TokenEncryptor.class);

    /** 源码中保留的本地开发默认密钥；启动期必须显式拒绝。 */
    private static final String DEV_DEFAULT_TOKEN_KEY =
            "dev-change-me-32-bytes-token-encryption-key!!";

    private final String tokenEncryptionKey;

    private AesGcmCipher cipher;

    public TokenEncryptor(@Value("${app.security.token-encryption-key}") String tokenEncryptionKey) {
        this.tokenEncryptionKey = tokenEncryptionKey;
    }

    /** 启动期构造底层加密器；任何失败均阻止上下文启动。 */
    @PostConstruct
    void init() {
        if (DEV_DEFAULT_TOKEN_KEY.equals(tokenEncryptionKey)) {
            throw new IllegalStateException(
                    "app.security.token-encryption-key uses the known development default; set TOKEN_ENCRYPTION_KEY explicitly");
        }
        this.cipher = new AesGcmCipher(tokenEncryptionKey);
        log.info("TokenEncryptor initialized (AES-GCM-256, PBKDF2-HMAC-SHA256)");
    }

    /**
     * 加密任意明文字符串。{@code null} 透传为 {@code null}。
     *
     * @param plain 明文（如 accessToken / apiKey / webhookSecret 原文）
     * @return base64 编码的密文，可直接落库于 {@code *_encrypted} 列
     */
    public String encrypt(String plain) {
        return cipher.encrypt(plain);
    }

    /**
     * 解密 base64 密文，得到原始明文。{@code null} 透传为 {@code null}。
     *
     * @param b64 base64 密文（来自数据库 {@code *_encrypted} 列）
     * @return 原始明文
     * @throws IllegalArgumentException 当密文格式非法或被篡改
     */
    public String decrypt(String b64) {
        return cipher.decrypt(b64);
    }

    /**
     * 判定给定密文是否能被当前密钥成功解密。
     *
     * <p>用于密钥轮换 / 数据迁移阶段：批量扫描历史密文时无需依赖异常控制流。
     *
     * @param b64 base64 密文；{@code null} 视为不可解密
     * @return {@code true} 当且仅当能成功解密
     */
    public boolean canDecrypt(String b64) {
        return cipher.canDecrypt(b64);
    }
}
