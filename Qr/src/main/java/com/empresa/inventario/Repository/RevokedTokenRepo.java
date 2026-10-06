package com.empresa.inventario.Repository;

import com.empresa.inventario.Model.RevokedToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface RevokedTokenRepo extends JpaRepository<RevokedToken, String> {

    // Indica si un access token (por su jti) fue revocado via logout.
    boolean existsByJti(String jti);

    // Purga de tokens ya expirados: la blacklist no debe crecer indefinidamente.
    void deleteByExpiresAtBefore(Instant now);
}
