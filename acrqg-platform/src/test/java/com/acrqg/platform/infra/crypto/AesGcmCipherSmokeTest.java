package com.acrqg.platform.infra.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link AesGcmCipher} 冒烟测试（不依赖 Spring 上下文）。
 *
 * <p>仅保证 B0-A.5 的核心不变式：往返一致、IV 随机、Tag 校验、构造前置校验。
 * 完整的属性测试 / 性能测试由 B6 安全审计批次补充。
 *
 * <p>Covers: R5.3, R5.4, R21.1, R23.2。
 */
class AesGcmCipherSmokeTest {

    /** 32 字节 UTF-8 口令，满足 design.md §13.2 推荐长度。 */
    private static final String PASSPHRASE = "unit-test-passphrase-1234567890!!";

    private final AesGcmCipher cipher = new AesGcmCipher(PASSPHRASE);

    @Test
    @DisplayName("ASCII 明文加解密往返一致")
    void roundTripAscii() {
        String plain = "ghp_AbCdEfGhIjKlMnOpQrStUvWxYz0123456789";
        String b64 = cipher.encrypt(plain);
        assertThat(b64).isNotEqualTo(plain);
        assertThat(cipher.decrypt(b64)).isEqualTo(plain);
        assertThat(cipher.canDecrypt(b64)).isTrue();
    }

    @Test
    @DisplayName("UTF-8 含中文明文加解密往返一致")
    void roundTripUtf8Chinese() {
        String plain = "门禁密钥-测试-α-β-✓";
        String b64 = cipher.encrypt(plain);
        assertThat(cipher.decrypt(b64)).isEqualTo(plain);
    }

    @Test
    @DisplayName("null 明文 / 密文均透传 null")
    void nullPassthrough() {
        assertThat(cipher.encrypt(null)).isNull();
        assertThat(cipher.decrypt(null)).isNull();
        assertThat(cipher.canDecrypt(null)).isFalse();
    }

    @Test
    @DisplayName("同一明文每次加密产生不同密文（IV 随机）")
    void ivIsRandomized() {
        String plain = "same-plaintext";
        String c1 = cipher.encrypt(plain);
        String c2 = cipher.encrypt(plain);
        String c3 = cipher.encrypt(plain);
        assertThat(c1).isNotEqualTo(c2);
        assertThat(c2).isNotEqualTo(c3);
        assertThat(c1).isNotEqualTo(c3);
        // 三个密文最终都能解密回同一明文
        assertThat(cipher.decrypt(c1)).isEqualTo(plain);
        assertThat(cipher.decrypt(c2)).isEqualTo(plain);
        assertThat(cipher.decrypt(c3)).isEqualTo(plain);
    }

    @Test
    @DisplayName("密文末字节被篡改 -> decrypt 抛 IllegalArgumentException")
    void tamperedCiphertextRejected() {
        String b64 = cipher.encrypt("secret-payload");
        byte[] raw = Base64.getDecoder().decode(b64);
        // 翻转最末字节最低位（位于 GCM Tag 区域，必然导致 AEADBadTagException）
        raw[raw.length - 1] ^= 0x01;
        String tampered = Base64.getEncoder().encodeToString(raw);

        assertThatThrownBy(() -> cipher.decrypt(tampered))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("authentication failed");
        assertThat(cipher.canDecrypt(tampered)).isFalse();
    }

    @Test
    @DisplayName("密文长度不足 IV+Tag -> decrypt 抛 IllegalArgumentException")
    void tooShortCiphertextRejected() {
        // 10 字节 base64：远小于 IV(12) + Tag(16) 的最小 28 字节门槛
        String shortB64 = Base64.getEncoder().encodeToString(new byte[]{1, 2, 3, 4, 5});
        assertThatThrownBy(() -> cipher.decrypt(shortB64))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too short");
    }

    @Test
    @DisplayName("非法 base64 -> decrypt 抛 IllegalArgumentException")
    void invalidBase64Rejected() {
        assertThatThrownBy(() -> cipher.decrypt("not!!base64@@"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid base64");
    }

    @Test
    @DisplayName("构造时 passphrase 为 null -> 抛 IllegalArgumentException")
    void rejectNullPassphrase() {
        assertThatThrownBy(() -> new AesGcmCipher(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be null");
    }

    @Test
    @DisplayName("构造时 passphrase 空白 -> 抛 IllegalArgumentException")
    void rejectBlankPassphrase() {
        assertThatThrownBy(() -> new AesGcmCipher("    "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("构造时 passphrase 长度不足 16 字节 -> 抛 IllegalArgumentException")
    void rejectShortPassphrase() {
        assertThatThrownBy(() -> new AesGcmCipher("short"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least");
    }

    @Test
    @DisplayName("不同 passphrase 派生不同密钥：互相不能解密对方密文")
    void differentPassphraseCannotDecrypt() {
        AesGcmCipher other = new AesGcmCipher("OTHER-passphrase-abcdefghij1234567890");
        String b64 = cipher.encrypt("hello");
        assertThat(other.canDecrypt(b64)).isFalse();
        assertThatThrownBy(() -> other.decrypt(b64))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("空字符串明文也能正确往返")
    void emptyStringRoundTrip() {
        String b64 = cipher.encrypt("");
        assertThat(b64).isNotNull();
        assertThat(cipher.decrypt(b64)).isEmpty();
    }
}
