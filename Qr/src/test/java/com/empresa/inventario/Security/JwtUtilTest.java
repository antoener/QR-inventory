package com.empresa.inventario.Security;

import com.empresa.inventario.Enums.Role;
import com.empresa.inventario.Model.User;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtUtilTest {

    // 64 bytes (>= 32 requeridos por HS256).
    private final String secret = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private final JwtUtil jwtUtil = new JwtUtil(secret, 15, 7);

    private User buildUser() {
        User user = new User();
        user.setId(1L);
        user.setUsername("jperez");
        user.setRole(Role.ADMIN);
        user.setPasswordChangedAt(Instant.now());
        return user;
    }

    @Test
    void generateAccessToken_deberiaIncluirUserIdRolePwdAtYJti() {
        User user = buildUser();
        String token = jwtUtil.generateAccessToken(user);

        Claims claims = jwtUtil.parseAccessToken(token);

        assertEquals(1L, jwtUtil.extractUserId(claims));
        assertEquals("ADMIN", jwtUtil.extractRole(claims));
        assertEquals(user.getPasswordChangedAt().toEpochMilli(), jwtUtil.extractPasswordChangedAt(claims));
        assertNotNull(jwtUtil.extractJti(claims));
        assertTrue(jwtUtil.extractExpiration(claims).isAfter(Instant.now()));
    }

    @Test
    void parseAccessToken_deberiaLanzarExcepcionConTokenManipulado() {
        String token = jwtUtil.generateAccessToken(buildUser());
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThrows(Exception.class, () -> jwtUtil.parseAccessToken(tampered));
    }

    @Test
    void generateRefreshToken_deberiaGenerarTokensDistintos() {
        String first = jwtUtil.generateRefreshToken();
        String second = jwtUtil.generateRefreshToken();

        assertNotNull(first);
        assertNotNull(second);
        assertTrue(!first.equals(second));
    }
}
