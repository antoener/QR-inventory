package com.empresa.inventario.Security;

import com.empresa.inventario.Model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

// Emite y valida JWTs de acceso y refresh tokens opacos. El access token es un
// JWT firmado HS256; el refresh token es una cadena aleatoria que se guarda
// hasheada en BD y se rota en cada uso.
@Component
public class JwtUtil {

    private final SecretKey signingKey;
    private final long accessExpirationMinutes;
    private final long refreshExpirationDays;
    private final SecureRandom secureRandom = new SecureRandom();

    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-expiration-minutes:15}") long accessExpirationMinutes,
            @Value("${jwt.refresh-expiration-days:7}") long refreshExpirationDays) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpirationMinutes = accessExpirationMinutes;
        this.refreshExpirationDays = refreshExpirationDays;
    }

    // Access token: sub=userId, role, pwdAt (epoch millis del ultimo cambio de
    // password, para invalidar tokens viejos), jti (para revocacion por logout).
    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("role", user.getRole().name())
                .claim("pwdAt", user.getPasswordChangedAt().toEpochMilli())
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessExpirationMinutes, ChronoUnit.MINUTES)))
                .signWith(signingKey)
                .compact();
    }

    // Refresh token opaco (256 bits en Base64 URL). Se guarda solo su hash.
    public String generateRefreshToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // Duracion en minutos del access token (para armar el blacklist TTL).
    public long accessExpirationMinutes() {
        return accessExpirationMinutes;
    }

    // Duracion en dias del refresh token (para setear expiresAt al guardarlo).
    public long refreshExpirationDays() {
        return refreshExpirationDays;
    }

    // Parsea y verifica la firma. Lanza JwtException/IllegalArgumentException si
    // el token es invalido o expiro.
    public Claims parseAccessToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long extractUserId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    public String extractRole(Claims claims) {
        return claims.get("role", String.class);
    }

    public String extractJti(Claims claims) {
        return claims.getId();
    }

    public Long extractPasswordChangedAt(Claims claims) {
        return claims.get("pwdAt", Long.class);
    }

    public Instant extractExpiration(Claims claims) {
        return claims.getExpiration().toInstant();
    }
}
