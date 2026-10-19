package com.guangqian.springkb.security;

import com.guangqian.springkb.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * JWT 签发与校验（HS256）
 */
@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtUtil {

    private String secret;

    @DurationUnit(ChronoUnit.HOURS)
    private Duration expirationHours;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.username())
                .claim("role", user.role())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expirationHours)))
                .signWith(key())
                .compact();
    }

    /**
     * 校验并解析 token；无效/过期时抛出异常
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public void setExpirationHours(Duration expirationHours) {
        this.expirationHours = expirationHours;
    }
}
