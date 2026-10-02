package com.zhiyu.common.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link AesEncryptor} 单测（T1.3a）。
 */
class AesEncryptorTest {

    private AesEncryptor encryptor;

    @BeforeEach
    void setUp() {
        // 32 字节 base64 编码密钥（test-only）
        encryptor = new AesEncryptor("dGhpcy1pcy1hLXRlc3Qtb25seS1rZXktMzJieXRlcyE=");
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
