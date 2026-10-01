package com.zhiyu.common.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 密码哈希工具，统一封装 BCrypt。
 *
 * <p>只依赖 {@code spring-security-crypto}（不是完整的 Spring Security 框架），
 * 业务代码不要直接 new {@link BCryptPasswordEncoder}，一律走本类。
 *
 * <p>哈希结果为标准 BCrypt 串（默认 strength = 10，前缀 {@code $2a$}），
 * 直接存入 {@code user.password VARCHAR(255)}。
 */
public final class PasswordUtil {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    private PasswordUtil() {
    }

    /** 明文 → BCrypt 哈希。 */
    public static String encode(String rawPassword) {
        return ENCODER.encode(rawPassword);
    }

    /** 校验明文与哈希是否匹配。 */
    public static boolean matches(String rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        return ENCODER.matches(rawPassword, encodedPassword);
    }
}
