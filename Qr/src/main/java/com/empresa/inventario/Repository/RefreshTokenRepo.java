package com.empresa.inventario.Repository;

import com.empresa.inventario.Model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepo extends JpaRepository<RefreshToken, Long> {

    // Busca un refresh token por su hash SHA-256 (para la rotacion).
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // Revoca toda la familia de refresh tokens de un usuario (reuso detectado,
    // cambio de password o logout).
    void deleteByUserId(Long userId);

    // Purga de refresh tokens expirados.
    void deleteByExpiresAtBefore(Instant now);
}
