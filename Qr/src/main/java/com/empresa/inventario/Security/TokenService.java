package com.empresa.inventario.Security;

import com.empresa.inventario.Model.RefreshToken;
import com.empresa.inventario.Model.RevokedToken;
import com.empresa.inventario.Repository.RefreshTokenRepo;
import com.empresa.inventario.Repository.RevokedTokenRepo;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

// Centraliza la persistencia de tokens: blacklist de access tokens (por jti) y
// el ciclo de vida de los refresh tokens (guardado con hash, rotacion y revoca-
// cion). Tambien purga las tablas cuando los tokens expiran.
@Service
public class TokenService {

    private final RevokedTokenRepo revokedTokenRepo;
    private final RefreshTokenRepo refreshTokenRepo;

    public TokenService(RevokedTokenRepo revokedTokenRepo, RefreshTokenRepo refreshTokenRepo) {
        this.revokedTokenRepo = revokedTokenRepo;
        this.refreshTokenRepo = refreshTokenRepo;
    }

    // Blacklist de access tokens (logout). Idempotente: si el jti ya existe no falla.
    @Transactional
    public void revokeAccessToken(String jti, Instant expiresAt) {
        if (jti == null || jti.isBlank() || revokedTokenRepo.existsByJti(jti)) {
            return;
        }
        RevokedToken revoked = new RevokedToken();
        revoked.setJti(jti);
        revoked.setExpiresAt(expiresAt);
        revokedTokenRepo.save(revoked);
    }

    // Indica si un access token fue revocado via logout.
    @Transactional(readOnly = true)
    public boolean isAccessTokenRevoked(String jti) {
        return jti != null && !jti.isBlank() && revokedTokenRepo.existsByJti(jti);
    }

    // Guarda un refresh token nuevo (solo su hash SHA-256).
    @Transactional
    public void saveRefreshToken(Long userId, String rawToken, Instant expiresAt) {
        RefreshToken token = new RefreshToken();
        token.setTokenHash(sha256(rawToken));
        token.setUserId(userId);
        token.setExpiresAt(expiresAt);
        refreshTokenRepo.save(token);
    }

    // Busca el refresh token por su raw token (comparando el hash).
    @Transactional(readOnly = true)
    public Optional<RefreshToken> findRefreshToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        return refreshTokenRepo.findByTokenHash(sha256(rawToken));
    }

    // Marca un refresh token como usado (rotado). Es el paso previo a emitir uno nuevo.
    @Transactional
    public void markRefreshTokenRevoked(RefreshToken token) {
        token.setRevoked(true);
        refreshTokenRepo.save(token);
    }

    // Revoca toda la familia de refresh tokens de un usuario (reuso detectado,
    // cambio de password o logout).
    @Transactional
    public void revokeAllRefreshTokensForUser(Long userId) {
        refreshTokenRepo.deleteByUserId(userId);
    }

    // Purga periodica de tokens expirados (cada hora).
    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void purgeExpiredTokens() {
        Instant now = Instant.now();
        revokedTokenRepo.deleteByExpiresAtBefore(now);
        refreshTokenRepo.deleteByExpiresAtBefore(now);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
