package com.zhiyu.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 签发与校验（无状态，不落库、不用 Redis）。
 *
 * <p>算法固定 HS256；密钥与有效期来自配置
 * {@code zhiyu.jwt.secret}（环境变量 {@code JWT_SECRET}）与
 * {@code zhiyu.jwt.expire-hours}（环境变量 {@code JWT_EXPIRE_HOURS}，默认 12）。
 *
 * <p>payload 只放 {@code sub}=userId 与 {@code role} 两个字段：
 * token 是客户端可读的（Base64，不是加密），<b>绝不放手机号、学校等个人信息</b>，
 * 也不能把 role 当作可信授权依据之外的业务数据源。
 *
 * <p>密钥未配置或不足 32 字节时<b>启动即失败</b>（fail-fast），
 * 避免带着一个可被暴力破解的密钥上线。
 */
@Slf4j
@Component
public class JwtUtil {

    /** HS256 的密钥下限：256 bit = 32 字节。 */
    private static final int MIN_SECRET_BYTES = 32;

    /** 角色码的 claim 名，与 AGENTS.md §3.1 的角色码对应。 */
    private static final String CLAIM_ROLE = "role";

    private final SecretKey key;
    private final long expireMillis;

    public JwtUtil(@Value("${zhiyu.jwt.secret}") String secret,
                   @Value("${zhiyu.jwt.expire-hours}") long expireHours) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "zhiyu.jwt.secret 未配置：请通过环境变量 JWT_SECRET 注入（见 .env.example）");
        }
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "zhiyu.jwt.secret 不足 " + MIN_SECRET_BYTES + " 字节（HS256 要求），当前 "
                            + secretBytes.length + " 字节");
        }
        if (expireHours <= 0) {
            throw new IllegalStateException(
                    "zhiyu.jwt.expire-hours 必须为正数，当前 " + expireHours);
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.expireMillis = expireHours * 60L * 60L * 1000L;
        log.info("JwtUtil 初始化完成：算法 HS256，token 有效期 {} 小时", expireHours);
    }

    /**
     * 签发 token。
     *
     * @param userId 用户主键（{@code user.id}）
     * @param role   角色码，见 AGENTS.md §3.1
     */
    public String createToken(Long userId, Integer role) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_ROLE, role)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expireMillis))
                .signWith(key)
                .compact();
    }

    /**
     * 解析并校验 token。
     *
     * <p>签名错误、已过期、格式非法、subject 不是数字……一律返回 {@code null}，
     * <b>不向调用方抛异常</b>：过滤器据此判定「未认证」，由
     * {@link RestAuthenticationEntryPoint} 统一输出 401 + {@code Result(1002)}。
     * 异常细节只记 debug 日志，不返回给客户端（避免泄露校验细节）。
     */
    public LoginUser parseToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Long userId = Long.valueOf(claims.getSubject());
            Integer role = claims.get(CLAIM_ROLE, Integer.class);
            return new LoginUser(userId, role);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT 校验未通过：{}", e.getMessage());
            return null;
        }
    }
}
