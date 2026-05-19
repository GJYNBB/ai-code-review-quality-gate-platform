package com.acrqg.platform.infra.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-GCM-256 认证加密工具（纯 helper，非 Spring 组件）。
 *
 * <p>布局：{@code base64( IV || CipherText || GCM_TAG )}，
 * 其中 IV 长度 12 字节（GCM 推荐），GCM Tag 长度 128 bit（默认行为，由 JCE 自动追加在密文末尾）。
 *
 * <p>密钥派生：通过 {@code PBKDF2WithHmacSHA256} 由 {@code masterPassphrase} +
 * 应用级固定 salt（{@value #SALT_LITERAL}）派生 256-bit AES 密钥，
 * 迭代次数 {@value #PBKDF2_ITERATIONS}。
 *
 * <p><b>盐策略说明（重要）</b>：salt 取自固定字面量
 * {@value #SALT_LITERAL}，与 masterPassphrase 一起决定派生密钥。
 * 一旦修改 salt 字面量或 PBKDF2 参数，
 * 历史已落库的密文将无法再被解密；如需轮换，须先用旧密钥解密、再用新密钥加密回写。
 * salt 不需要保密——其安全性来自 masterPassphrase 自身的熵以及 PBKDF2 的迭代次数。
 *
 * <p><b>不变式</b>：对任意有效输入 {@code x}，{@code decrypt(encrypt(x)).equals(x)}；
 * 对同一 {@code x} 的多次 {@code encrypt(x)}，由于 IV 随机，输出密文必不同。
 *
 * <p><b>错误处理</b>：
 * <ul>
 *   <li>{@link #encrypt(String)} 接收 {@code null} 时返回 {@code null}（透传，不抛异常）。</li>
 *   <li>{@link #decrypt(String)} 接收 {@code null} 时返回 {@code null}。</li>
 *   <li>{@link #decrypt(String)} 在密文长度不足或被篡改时抛
 *       {@link IllegalArgumentException}（业务输入错误）。</li>
 *   <li>底层 {@link GeneralSecurityException} 视为程序/配置错误，
 *       包装为 {@link IllegalStateException}（不映射为业务 ErrorCode）。</li>
 * </ul>
 *
 * <p>Covers: R5.3 (accessToken/webhookSecret 加密存储), R5.4 (响应中不返回明文),
 * R21.1 (apiKey 加密存储), R23.2 (对称加密算法 + 来自 tokenEncryptionKey 的密钥)。
 *
 * @see TokenEncryptor 应用层薄包装，从 {@code application.yml} 注入 passphrase。
 */
public final class AesGcmCipher {

    /** GCM 推荐 IV 长度（字节）。 */
    public static final int IV_LEN_BYTES = 12;

    /** GCM 认证 Tag 长度（bit）。 */
    public static final int GCM_TAG_BITS = 128;

    /** GCM 认证 Tag 长度（字节），用于密文最小长度校验。 */
    public static final int GCM_TAG_BYTES = GCM_TAG_BITS / 8;

    /** 派生密钥长度（bit）。AES-256。 */
    public static final int KEY_LEN_BITS = 256;

    /** PBKDF2 迭代次数。 */
    public static final int PBKDF2_ITERATIONS = 65_536;

    /** PBKDF2 算法名（HMAC-SHA256）。 */
    public static final String PBKDF2_ALGO = "PBKDF2WithHmacSHA256";

    /** AES-GCM 算法名（无填充，GCM 模式自身处理）。 */
    public static final String CIPHER_ALGO = "AES/GCM/NoPadding";

    /** 应用级固定 salt 字面量。修改将使所有历史密文不可解。 */
    static final String SALT_LITERAL = "acrqg-platform-aes-gcm-v1";

    /** masterPassphrase 最小字节长度（UTF-8）。设计 §13.2 推荐 ≥ 32。 */
    public static final int MIN_PASSPHRASE_BYTES = 16;

    /**
     * 单实例 SecureRandom：避免 {@code SecureRandom.getInstanceStrong()} 每次加密都
     * 阻塞读取 {@code /dev/random}（Linux）。{@link SecureRandom} 默认实现已是
     * 密码学安全 PRNG，足以满足 GCM IV 随机性要求。
     */
    private static final SecureRandom RNG = new SecureRandom();

    private final SecretKey key;

    /**
     * 构造加密器并派生 AES-256 密钥。
     *
     * @param masterPassphrase 主口令（来自系统参数 {@code tokenEncryptionKey}）；
     *                         不能为 {@code null} / 空白；UTF-8 字节长度必须 ≥
     *                         {@value #MIN_PASSPHRASE_BYTES}。
     * @throws IllegalArgumentException 当 passphrase 为空或过短
     * @throws IllegalStateException    当 JCE 不可用（极罕见）
     */
    public AesGcmCipher(String masterPassphrase) {
        if (masterPassphrase == null || masterPassphrase.isBlank()) {
            throw new IllegalArgumentException("masterPassphrase must not be null or blank");
        }
        byte[] passBytes = masterPassphrase.getBytes(StandardCharsets.UTF_8);
        if (passBytes.length < MIN_PASSPHRASE_BYTES) {
            throw new IllegalArgumentException(
                    "masterPassphrase must be at least " + MIN_PASSPHRASE_BYTES
                            + " bytes (UTF-8), current=" + passBytes.length);
        }
        this.key = deriveKey(masterPassphrase.toCharArray());
    }

    private static SecretKey deriveKey(char[] passphrase) {
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2_ALGO);
            byte[] salt = SALT_LITERAL.getBytes(StandardCharsets.UTF_8);
            KeySpec spec = new PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, KEY_LEN_BITS);
            byte[] raw = factory.generateSecret(spec).getEncoded();
            return new SecretKeySpec(raw, "AES");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("aes-gcm key derivation failed", e);
        } finally {
            // 尽量擦除栈上的口令副本（passphrase 是构造方法 toCharArray 拷贝出来的）
            Arrays.fill(passphrase, '\0');
        }
    }

    /**
     * 加密。{@code null} 透传为 {@code null}。
     *
     * <p>每次调用生成新的 12 字节随机 IV，输出格式为
     * {@code base64( IV || CipherText || Tag )}。
     *
     * @param plain 明文（任意 UTF-8 字符串）
     * @return base64 编码的密文；输入 null 时返回 null
     */
    public String encrypt(String plain) {
        if (plain == null) {
            return null;
        }
        byte[] iv = new byte[IV_LEN_BYTES];
        RNG.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] all = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, all, 0, iv.length);
            System.arraycopy(ct, 0, all, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(all);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("aes-gcm encrypt failed", e);
        }
    }

    /**
     * 解密。{@code null} 透传为 {@code null}。
     *
     * @param b64Cipher base64 编码的 {@code IV || CipherText || Tag}
     * @return 明文 UTF-8 字符串；输入 null 时返回 null
     * @throws IllegalArgumentException 当 base64 非法、长度不足、或认证 Tag 校验失败
     * @throws IllegalStateException    底层 JCE 出现非 Tag 类异常时
     */
    public String decrypt(String b64Cipher) {
        if (b64Cipher == null) {
            return null;
        }
        byte[] all;
        try {
            all = Base64.getDecoder().decode(b64Cipher);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid base64 ciphertext", e);
        }
        // IV(12) + 至少一个 GCM Tag(16) 字节 —— 即便明文长度为 0，密文长度也至少 = IV + Tag
        if (all.length < IV_LEN_BYTES + GCM_TAG_BYTES) {
            throw new IllegalArgumentException("invalid ciphertext: too short");
        }
        byte[] iv = Arrays.copyOfRange(all, 0, IV_LEN_BYTES);
        byte[] ct = Arrays.copyOfRange(all, IV_LEN_BYTES, all.length);
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_ALGO);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] plain = cipher.doFinal(ct);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (AEADBadTagException e) {
            // GCM Tag 不匹配 —— 密文被篡改或密钥不一致；视为业务级输入错误
            throw new IllegalArgumentException("ciphertext authentication failed", e);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("aes-gcm decrypt failed", e);
        }
    }

    /**
     * 判定给定 base64 串是否能被本实例成功解密。
     *
     * <p>典型用途：在密钥轮换 / 迁移流程中安全地探测某段密文是否仍然属于
     * 当前密钥，而无需 try/catch 包裹业务逻辑。任何异常一律视为不可解密。
     *
     * @param b64Cipher base64 密文；{@code null} 视为不可解密
     * @return {@code true} 当且仅当 {@link #decrypt(String)} 不抛异常时
     */
    public boolean canDecrypt(String b64Cipher) {
        if (b64Cipher == null) {
            return false;
        }
        try {
            decrypt(b64Cipher);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
