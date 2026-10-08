package com.empresa.inventario.Services.Impls;

import com.empresa.inventario.Exceptions.InvalidCredentialsException;
import com.empresa.inventario.Exceptions.InvalidPasswordException;
import com.empresa.inventario.Model.PasswordResetToken;
import com.empresa.inventario.Model.User;
import com.empresa.inventario.Enums.Role;
import com.empresa.inventario.Repository.PasswordResetTokenRepo;
import com.empresa.inventario.Repository.UserRepo;
import com.empresa.inventario.Security.PasswordValidator;
import com.empresa.inventario.Security.TokenService;
import com.empresa.inventario.Services.MailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServImplForgotResetTest {

    @Mock private UserRepo userRepo;
    @Mock private PasswordResetTokenRepo passwordResetTokenRepo;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private TokenService tokenService;
    @Mock private MailService mailService;

    private AuthServImpl authService;
    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("jperez");
        testUser.setEmail("jperez@empresa.com");
        testUser.setName("Juan Perez");
        testUser.setActive(true);
        testUser.setPassword("$2a$12$hashedpassword");
        testUser.setMustChangePassword(false);
        testUser.setRole(Role.ADMIN);
        testUser.setCreatedAt(Instant.now());
        testUser.setPasswordChangedAt(Instant.now());

        authService = new AuthServImpl(
                userRepo,
                passwordResetTokenRepo,
                passwordEncoder,
                null, // jwtUtil not used
                tokenService,
                null, // securityContextService not used
                mailService,
                "http://localhost:3000",
                false);
    }

    @Test
    void forgotPassword_usuarioExiste_creaTokenYEnviaEmail() {
        when(userRepo.findByEmailIgnoreCase("jperez@empresa.com")).thenReturn(Optional.of(testUser));

        authService.forgotPassword("jperez@empresa.com");

        verify(userRepo).findByEmailIgnoreCase("jperez@empresa.com");
        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepo).save(tokenCaptor.capture());
        verify(mailService).send(eq("jperez@empresa.com"), anyString(), anyString());

        PasswordResetToken savedToken = tokenCaptor.getValue();
        assertThat(savedToken.getUser()).isEqualTo(testUser);
        assertThat(savedToken.getTokenHash()).isNotNull();
        assertThat(savedToken.getExpiresAt()).isAfter(Instant.now());
        assertThat(savedToken.getUsedAt()).isNull();
    }

    @Test
    void forgotPassword_usuarioNoExiste_noFalla_silencioso() {
        when(userRepo.findByEmailIgnoreCase("noexiste@empresa.com")).thenReturn(Optional.empty());

        authService.forgotPassword("noexiste@empresa.com");

        verify(userRepo).findByEmailIgnoreCase("noexiste@empresa.com");
        verifyNoInteractions(passwordResetTokenRepo, mailService);
    }

    @Test
    void forgotPassword_usuarioInactivo_noEnviaEmail() {
        testUser.setActive(false);
        when(userRepo.findByEmailIgnoreCase("jperez@empresa.com")).thenReturn(Optional.of(testUser));

        authService.forgotPassword("jperez@empresa.com");

        verify(userRepo).findByEmailIgnoreCase("jperez@empresa.com");
        verifyNoInteractions(passwordResetTokenRepo, mailService);
    }

    @Test
    void resetPassword_tokenValido_actualizaPasswordYRevocaTokens() {
        String rawToken = "a".repeat(64); // 32 bytes hex
        String tokenHash = hashToken(rawToken);

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setId(1L);
        resetToken.setUser(testUser);
        resetToken.setTokenHash(tokenHash);
        resetToken.setExpiresAt(Instant.now().plusSeconds(3600));
        resetToken.setUsedAt(null);

        when(passwordResetTokenRepo.findByTokenHash(tokenHash)).thenReturn(Optional.of(resetToken));
        when(passwordEncoder.encode("NuevaPass123!")).thenReturn("newHashed");

        authService.resetPassword(rawToken, "NuevaPass123!");

        assertThat(testUser.getPassword()).isEqualTo("newHashed");
        assertThat(testUser.isMustChangePassword()).isFalse();
        assertThat(testUser.getPasswordChangedAt()).isNotNull();

        verify(passwordResetTokenRepo).save(resetToken);
        assertThat(resetToken.getUsedAt()).isNotNull();

        verify(tokenService).revokeAllRefreshTokensForUser(1L);
    }

    @Test
    void resetPassword_tokenExpirado_lanza401() {
        String rawToken = "b".repeat(64);
        String tokenHash = hashToken(rawToken);

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setTokenHash(tokenHash);
        resetToken.setExpiresAt(Instant.now().minusSeconds(1)); // expirado
        resetToken.setUsedAt(null);

        when(passwordResetTokenRepo.findByTokenHash(tokenHash)).thenReturn(Optional.of(resetToken));

        assertThatThrownBy(() -> authService.resetPassword(rawToken, "NuevaPass123!"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Token inválido o expirado");

        verifyNoInteractions(passwordEncoder, tokenService);
    }

    @Test
    void resetPassword_tokenYaUsado_lanza401() {
        String rawToken = "c".repeat(64);
        String tokenHash = hashToken(rawToken);

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setTokenHash(tokenHash);
        resetToken.setExpiresAt(Instant.now().plusSeconds(3600));
        resetToken.setUsedAt(Instant.now().minusSeconds(100)); // ya usado

        when(passwordResetTokenRepo.findByTokenHash(tokenHash)).thenReturn(Optional.of(resetToken));

        assertThatThrownBy(() -> authService.resetPassword(rawToken, "NuevaPass123!"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Token inválido o expirado");
    }

    @Test
    void resetPassword_tokenInexistente_lanza401() {
        String rawToken = "d".repeat(64);
        String tokenHash = hashToken(rawToken);

        when(passwordResetTokenRepo.findByTokenHash(tokenHash)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.resetPassword(rawToken, "NuevaPass123!"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Token inválido o expirado");
    }

    @Test
    void resetPassword_passwordDebil_lanzaExcepcion() {
        String rawToken = "e".repeat(64);
        String tokenHash = hashToken(rawToken);

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setTokenHash(tokenHash);
        resetToken.setExpiresAt(Instant.now().plusSeconds(3600));
        resetToken.setUsedAt(null);
        resetToken.setUser(testUser);

        when(passwordResetTokenRepo.findByTokenHash(tokenHash)).thenReturn(Optional.of(resetToken));

        assertThatThrownBy(() -> authService.resetPassword(rawToken, "corta"))
                .isInstanceOf(InvalidPasswordException.class)
                .hasMessageContaining("8 y 72 caracteres");
    }

    @Test
    void resetPassword_passwordSinMayuscula_lanzaExcepcion() {
        String rawToken = "f".repeat(64);
        String tokenHash = hashToken(rawToken);

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setTokenHash(tokenHash);
        resetToken.setExpiresAt(Instant.now().plusSeconds(3600));
        resetToken.setUsedAt(null);
        resetToken.setUser(testUser);

        when(passwordResetTokenRepo.findByTokenHash(tokenHash)).thenReturn(Optional.of(resetToken));

        assertThatThrownBy(() -> authService.resetPassword(rawToken, "nuevapass123!"))
                .isInstanceOf(InvalidPasswordException.class)
                .hasMessageContaining("mayuscula");
    }

    @Test
    void resetPassword_passwordSinMinuscula_lanzaExcepcion() {
        String rawToken = "g".repeat(64);
        String tokenHash = hashToken(rawToken);

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setTokenHash(tokenHash);
        resetToken.setExpiresAt(Instant.now().plusSeconds(3600));
        resetToken.setUsedAt(null);
        resetToken.setUser(testUser);

        when(passwordResetTokenRepo.findByTokenHash(tokenHash)).thenReturn(Optional.of(resetToken));

        assertThatThrownBy(() -> authService.resetPassword(rawToken, "NUEVAPASS123!"))
                .isInstanceOf(InvalidPasswordException.class)
                .hasMessageContaining("minuscula");
    }

    @Test
    void resetPassword_passwordSinDigito_lanzaExcepcion() {
        String rawToken = "h".repeat(64);
        String tokenHash = hashToken(rawToken);

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setTokenHash(tokenHash);
        resetToken.setExpiresAt(Instant.now().plusSeconds(3600));
        resetToken.setUsedAt(null);
        resetToken.setUser(testUser);

        when(passwordResetTokenRepo.findByTokenHash(tokenHash)).thenReturn(Optional.of(resetToken));

        assertThatThrownBy(() -> authService.resetPassword(rawToken, "NuevaPass!"))
                .isInstanceOf(InvalidPasswordException.class)
                .hasMessageContaining("digito");
    }

    @Test
    void resetPassword_passwordSinEspecial_lanzaExcepcion() {
        String rawToken = "i".repeat(64);
        String tokenHash = hashToken(rawToken);

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setTokenHash(tokenHash);
        resetToken.setExpiresAt(Instant.now().plusSeconds(3600));
        resetToken.setUsedAt(null);
        resetToken.setUser(testUser);

        when(passwordResetTokenRepo.findByTokenHash(tokenHash)).thenReturn(Optional.of(resetToken));

        assertThatThrownBy(() -> authService.resetPassword(rawToken, "NuevaPass123"))
                .isInstanceOf(InvalidPasswordException.class)
                .hasMessageContaining("especial");
    }

    // Helper para replicar la logica de hash del servicio
    private String hashToken(String token) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}