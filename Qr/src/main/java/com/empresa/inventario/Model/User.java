package com.empresa.inventario.Model;
import com.empresa.inventario.Enums.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private boolean mustChangePassword = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.ADMIN;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    // Ultima actividad registrada. Se usa para el inactivity timeout (15 min):
    // si supera el umbral, el JwtAuthenticationFilter rechaza el token.
    private Instant lastActivityAt;

    // Momento del ultimo cambio de password. Sirve para invalidar todos los
    // access tokens emitidos antes de ese instante (claim pwdAt del JWT).
    @Column(nullable = false)
    private Instant passwordChangedAt;

    @PrePersist
    void onCreate() {
        if (passwordChangedAt == null) {
            passwordChangedAt = Instant.now();
        }
    }
}
