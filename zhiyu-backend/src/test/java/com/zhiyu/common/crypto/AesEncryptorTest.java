package com.zhiyu.common.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link AesEncryptor} 单测（T1.3a）。
 */
class AesEncryptorTest {

    /** application-dev.yml 的内置开发默认密钥（base64）。 */
    private static final String DEV_DEFAULT_KEY = "dGhpcy1pcy1hLWRldi1vbmx5LWtleS0zMmJ5dGVzISE=";
    /** application-test.yml 的内置单测默认密钥（base64）。 */
    private static final String TEST_DEFAULT_KEY = "dGhpcy1pcy1hLXRlc3Qtb25seS1rZXktMzJieXRlcyE=";

    private AesEncryptor encryptor;

    @BeforeEach
    void setUp() {
        // 32 字节 base64 编码密钥（test-only），单测显式允许内置默认密钥
        encryptor = new AesEncryptor(TEST_DEFAULT_KEY, true);
    }

    @Test
    @DisplayName("缺省（空）密钥：构造即失败，报错指明 FIELD_ENCRYPT_SECRET")
    void blankSecretFailsFast() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new AesEncryptor("", false));
        assertTrue(ex.getMessage().contains("FIELD_ENCRYPT_SECRET"));
        assertThrows(IllegalStateException.class, () -> new AesEncryptor("   ", false));
        assertThrows(IllegalStateException.class, () -> new AesEncryptor(null, false));
    }

    @Test
    @DisplayName("生产路径：命中内置默认密钥且未显式允许 → 强制失败")
    void wellKnownDefaultRejectedWithoutOptIn() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new AesEncryptor(DEV_DEFAULT_KEY, false));
        assertTrue(ex.getMessage().contains("allow-default-secret"));
        assertThrows(IllegalStateException.class,
                () -> new AesEncryptor(TEST_DEFAULT_KEY, false));
    }

    @Test
    @DisplayName("显式允许默认密钥：可构造且加解密正常（仅本地开发/单测）")
    void wellKnownDefaultAllowedWithOptIn() {
        AesEncryptor dev = new AesEncryptor(DEV_DEFAULT_KEY, true);
        String plaintext = "本地开发数据";
        assertEquals(plaintext, dev.decrypt(dev.encrypt(plaintext)));
    }

    @Test
    @DisplayName("非 32 字节密钥：构造即失败")
    void wrongLengthFailsFast() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new AesEncryptor(shortKey, false));
        assertTrue(ex.getMessage().contains("32"));
    }

    @Test
    @DisplayName("加密后解密 = 原值")
    void encryptDecryptRoundtrip() {
        String plaintext = "考试压力好大，感觉自己快撑不住了";
        String encrypted = encryptor.encrypt(plaintext);
        String decrypted = encryptor.decrypt(encrypted);
        assertEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("相同明文两次加密结果不同（随机 IV）")
    void sameInputDifferentCipher() {
        String plaintext = "同样的内容";
        String encrypted1 = encryptor.encrypt(plaintext);
        String encrypted2 = encryptor.encrypt(plaintext);
        assertNotEquals(encrypted1, encrypted2, "随机 IV 应使密文不同");
        // 但都能解密回同一明文
        assertEquals(plaintext, encryptor.decrypt(encrypted1));
        assertEquals(plaintext, encryptor.decrypt(encrypted2));
    }

    @Test
    @DisplayName("null 输入返回 null")
    void nullSafe() {
        assertNull(encryptor.encrypt(null));
        assertNull(encryptor.decrypt(null));
    }

    @Test
    @DisplayName("篡改 ciphertext 后解密抛异常（GCM 完整性）")
    void tamperDetection() {
        String plaintext = "这是一条需要保护的消息";
        String encrypted = encryptor.encrypt(plaintext);

        // 篡改密文（翻转最后一个字符）
        char[] chars = encrypted.toCharArray();
        char last = chars[chars.length - 1];
        chars[chars.length - 1] = last == 'A' ? 'B' : 'A';
        String tampered = new String(chars);

        assertThrows(RuntimeException.class, () -> encryptor.decrypt(tampered));
    }
}
