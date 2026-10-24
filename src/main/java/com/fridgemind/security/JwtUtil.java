package com.fridgemind.security;

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
 * JWT 工具类：生成和解析 Token
 * HMAC-SHA 签名，支持用户 Token（role=user）和管理员 Token（role=admin）。
 */
@Slf4j
@Component
public class JwtUtil {

    /** application.yml 里的本地默认密钥：生产沿用它等于公开签名密钥 */
    private static final String DEFAULT_SECRET = "fridgemind-jwt-secret-key-change-in-production-2024";

    private final SecretKey key;
    private final long expirationMs;

    public JwtUtil(@Value("${app.jwt.secret}") String secret,
                   @Value("${app.jwt.expiration-ms}") long expirationMs) {
        if (DEFAULT_SECRET.equals(secret)) {
            log.warn("JWT 密钥仍为默认值，仅供本地开发；生产环境请用环境变量 JWT_SECRET 覆盖为强随机字符串");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    /** Token 解析结果 */
    public record TokenInfo(Long userId, String role) {}

    /** 生成普通用户 Token */
    public String generateToken(Long userId) {
        return buildToken(String.valueOf(userId), "user");
    }

    /** 生成管理员 Token */
    public String generateAdminToken(Long adminId) {
        return buildToken(String.valueOf(adminId), "admin");
    }

    private String buildToken(String subject, String role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(subject)
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }

    /** 解析 Token，返回 userId 和 role */
    public TokenInfo parseToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            Long userId = Long.valueOf(claims.getSubject());
            String role = claims.get("role", String.class);
            return new TokenInfo(userId, role != null ? role : "user");
        } catch (JwtException e) {
            return null;
        }
    }

    /** 解析 Token 仅返回 userId（兼容老代码） */
    public Long parseUserId(String token) {
        TokenInfo info = parseToken(token);
        return info != null ? info.userId() : null;
    }
}
