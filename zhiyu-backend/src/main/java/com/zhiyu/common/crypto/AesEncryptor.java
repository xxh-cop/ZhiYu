package com.zhiyu.common.crypto;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;

/**
 * AES-256-GCM 字段级加密工具（T1.3a）。
 *
 * <p>用于加密敏感字段（对话内容、心情便签），密钥只存服务端。
 * 前端拿到的永远是服务端解密后的明文（AGENTS.md §5）。
 *
 * <p>存储格式：{@code Base64(IV + ciphertext + tag)}
 * <ul>
 *   <li>IV：12 字节随机，每次加密不同</li>
 *   <li>tag：128 位（16 字节），GCM 模式自动追加</li>
 * </ul>
 *
 * <p>密钥从 {@code zhiyu.crypto.secret} 读取（Base64 编码的 32 字节）。
 * 密钥为空或长度不为 32 字节 → 构造即抛异常，应用启动失败（fail-fast）。
 *
 * <p>仓库内置的开发/测试默认密钥（application-dev.yml / application-test.yml）属于公开值，
 * 默认被拒绝：仅当 {@code zhiyu.crypto.allow-default-secret=true}
 * （环境变量 {@code ZHIYU_CRYPTO_ALLOW_DEFAULT_SECRET}）显式开启时才允许使用，
 * 防止默认密钥被静默带进生产/演示环境。
 */
@Slf4j
@Component
public class AesEncryptor {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    /**
     * 仓库内公开的默认密钥（base64）：application-dev.yml 本地开发默认值、
     * application-test.yml 单测默认值。生产/演示环境禁止使用。
     */
    private static final Set<String> WELL_KNOWN_DEFAULT_SECRETS = Set.of(
            "dGhpcy1pcy1hLWRldi1vbmx5LWtleS0zMmJ5dGVzISE=",
            "dGhpcy1pcy1hLXRlc3Qtb25seS1rZXktMzJieXRlcyE=");

    private final SecretKeySpec keySpec;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesEncryptor(@Value("${zhiyu.crypto.secret:}") String secretBase64,
                        @Value("${zhiyu.crypto.allow-default-secret:false}") boolean allowDefaultSecret) {
        if (secretBase64 == null || secretBase64.isBlank()) {
            throw new IllegalStateException(
                    "zhiyu.crypto.secret 为空，请通过环境变量 FIELD_ENCRYPT_SECRET 设置加密密钥");
        }
        if (!allowDefaultSecret && WELL_KNOWN_DEFAULT_SECRETS.contains(secretBase64.trim())) {
            throw new IllegalStateException(
                    "zhiyu.crypto.secret 命中仓库内置的开发/测试默认密钥，禁止直接使用："
                            + "生产/演示环境必须通过环境变量 FIELD_ENCRYPT_SECRET 设置独立密钥；"
                            + "仅本地开发或单测可显式设置 zhiyu.crypto.allow-default-secret=true"
                            + "（环境变量 ZHIYU_CRYPTO_ALLOW_DEFAULT_SECRET=true）后沿用默认密钥");
        }
        byte[] keyBytes = Base64.getDecoder().decode(secretBase64);
        if (keyBytes.length != 32) {
            throw new IllegalStateException(
                    "zhiyu.crypto.secret 解码后必须为 32 字节，实际 " + keyBytes.length + " 字节");
        }
        this.keySpec = new SecretKeySpec(keyBytes, ALGORITHM);
        if (WELL_KNOWN_DEFAULT_SECRETS.contains(secretBase64.trim())) {
            log.warn("AesEncryptor 正在使用仓库内置的开发/测试默认密钥（allow-default-secret 已显式开启），仅限本地开发/单测");
        } else {
            log.info("AesEncryptor 初始化完成（AES-256-GCM）");
        }
    }

    /**
     * 加密明文，返回 {@code Base64(IV + ciphertext + tag)}。
     * 对 null 输入返回 null。
     */
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // IV + ciphertext(含 tag)
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("加密失败", e);
        }
    }

    /**
     * 解密 {@code Base64(IV + ciphertext + tag)}，返回明文。
     * 对 null 输入返回 null。
     *
     * @throws RuntimeException 如果密文被篡改或解密失败（GCM 完整性校验）
     */
    public String decrypt(String ciphertext) {
        if (ciphertext == null) {
            return null;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(ciphertext);
            byte[] iv = new byte[IV_LENGTH];
            byte[] ct = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            System.arraycopy(combined, IV_LENGTH, ct, 0, ct.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));

            byte[] plaintext = cipher.doFinal(ct);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("解密失败（密文可能被篡改）", e);
        }
    }
}
