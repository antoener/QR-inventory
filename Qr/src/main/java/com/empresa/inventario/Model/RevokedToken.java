package com.empresa.inventario.Model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

// Blacklist de access tokens revocados (logout). Guarda solo el jti (no el token
// en claro): el filtro rechaza cualquier token cuyo jti este aca. La fila se
// purga cuando expira el access token que representa.
@Entity
@Table(name = "revoked_token", indexes = @Index(name = "idx_revoked_token_expires_at", columnList = "expires_at"))
@Getter
@Setter
@NoArgsConstructor
public class RevokedToken {

    @Id
    @Column(length = 36)
    private String jti;

    @Column(nullable = false, updatable = false)
    private Instant revokedAt;

    // Expira igual que el access token revocado: sirve para purgar la tabla.
    @Column(nullable = false)
    private Instant expiresAt;

    @PrePersist
    void onCreate() {
        if (revokedAt == null) {
            revokedAt = Instant.now();
        }
    }
}
