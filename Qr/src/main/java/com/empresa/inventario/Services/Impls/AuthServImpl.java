package com.empresa.inventario.Services.Impls;

import com.empresa.inventario.DTOs.Request.ChangePasswordRequest;
import com.empresa.inventario.DTOs.Request.LoginRequest;
import com.empresa.inventario.DTOs.Response.AuthResponse;
import com.empresa.inventario.DTOs.Response.UserResponse;
import com.empresa.inventario.Exceptions.InvalidCredentialsException;
import com.empresa.inventario.Exceptions.InvalidPasswordException;
import com.empresa.inventario.Model.PasswordResetToken;
import com.empresa.inventario.Model.RefreshToken;
import com.empresa.inventario.Model.User;
import com.empresa.inventario.Repository.PasswordResetTokenRepo;
import com.empresa.inventario.Repository.UserRepo;
import com.empresa.inventario.Security.JwtUtil;
import com.empresa.inventario.Security.PasswordValidator;
import com.empresa.inventario.Security.SecurityContextService;
import com.empresa.inventario.Security.TokenService;
import com.empresa.inventario.Services.IAuthService;
import com.empresa.inventario.Services.MailService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class AuthServImpl implements IAuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthServImpl.class);
    private static final String REFRESH_COOKIE = "refresh_token";
    private static final String COOKIE_PATH = "/api/auth";
    private static final int TOKEN_BYTES = 32;
    private static final long TOKEN_EXPIRY_HOURS = 1;

    private final UserRepo userRepo;
    private final PasswordResetTokenRepo passwordResetTokenRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final TokenService tokenService;
    private final SecurityContextService securityContextService;
    private final MailService mailService;
    private final SecureRandom secureRandom = new SecureRandom();
    private final String frontendUrl;
    private final boolean cookieSecure;

    public AuthServImpl(
            UserRepo userRepo,
            PasswordResetTokenRepo passwordResetTokenRepo,
            PasswordEncoder passwordEncoder,
            JwtUtil jwtUtil,
            TokenService tokenService,
            SecurityContextService securityContextService,
            MailService mailService,
            @Value("${app.frontend.url:http://localhost:3000}") String frontendUrl,
            @Value("${app.security.cookie-secure:true}") boolean cookieSecure) {
        this.userRepo = userRepo;
        this.passwordResetTokenRepo = passwordResetTokenRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.tokenService = tokenService;
        this.securityContextService = securityContextService;
        this.mailService = mailService;
        this.frontendUrl = frontendUrl;
        this.cookieSecure = cookieSecure;
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request, HttpServletResponse response) {
        String identifier = request.getIdentifier().trim();

        // 1. Busca case-insensitive para encontrar el usuario (anti-enumeración)
        User user = userRepo.findByUsernameIgnoreCase(identifier)
                .or(() -> userRepo.findByEmailIgnoreCase(identifier))
                .orElseThrow(() -> new InvalidCredentialsException("Credenciales invalidas"));

        // 2. Validación case-sensitive: el identifier debe coincidir exactamente
        // con username O email (no solo case-insensitive)
        boolean matchesUsername = user.getUsername().equals(identifier);
        boolean matchesEmail = user.getEmail().equals(identifier);

        if (!matchesUsername && !matchesEmail) {
            throw new InvalidCredentialsException("Credenciales invalidas");
        }

        if (!user.isActive() || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException("Credenciales invalidas");
        }

        user.setLastActivityAt(Instant.now());
        userRepo.save(user);

        return issueTokens(user, response);
    }

    @Override
    @Transactional
    public AuthResponse refresh(String refreshToken, HttpServletResponse response) {
        RefreshToken stored = tokenService.findRefreshToken(refreshToken)
                .orElseThrow(() -> new InvalidCredentialsException("Sesion invalida"));

        // Reuso detectado: el token ya fue rotado → se revoca toda la familia.
        if (stored.isRevoked()) {
            tokenService.revokeAllRefreshTokensForUser(stored.getUserId());
            logger.warn("Reuso de refresh token detectado: userId={}", stored.getUserId());
            throw new InvalidCredentialsException("Sesion invalida");
        }
        if (stored.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidCredentialsException("Sesion expirada");
        }

        // Rotacion: revoca el viejo y emite uno nuevo.
        tokenService.markRefreshTokenRevoked(stored);

        User user = userRepo.findById(stored.getUserId())
                .filter(User::isActive)
                .orElseThrow(() -> new InvalidCredentialsException("Sesion invalida"));

        return issueTokens(user, response);
    }

    @Override
    @Transactional
    public void logout(String accessToken, String refreshToken, HttpServletResponse response) {
        // Blacklist del access token por su jti (hasta su expiracion natural).
        if (accessToken != null && accessToken.startsWith("Bearer ")) {
            try {
                Claims claims = jwtUtil.parseAccessToken(accessToken.substring(7));
                tokenService.revokeAccessToken(jwtUtil.extractJti(claims), jwtUtil.extractExpiration(claims));
            } catch (Exception e) {
                logger.warn("Logout: no se pudo blacklistear el access token", e);
            }
        }

        // Revoca el refresh token actual (si existe).
        tokenService.findRefreshToken(refreshToken).ifPresent(tokenService::markRefreshTokenRevoked);

        clearRefreshCookie(response);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse me() {
        User user = securityContextService.getCurrentUser()
                .orElseThrow(() -> new InvalidCredentialsException("No autenticado"));
        return toResponse(user);
    }

    @Override
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        User user = securityContextService.getCurrentUser()
                .orElseThrow(() -> new InvalidCredentialsException("No autenticado"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new InvalidCredentialsException("Contrasena actual incorrecta");
        }

        PasswordValidator.validate(request.getNewPassword());

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setMustChangePassword(false);
        // Invalida todos los access tokens emitidos antes de este instante (claim pwdAt).
        user.setPasswordChangedAt(Instant.now());
        userRepo.save(user);

        // Revoca todos los refresh tokens: fuerza re-login en todos los dispositivos.
        tokenService.revokeAllRefreshTokensForUser(user.getId());
        logger.info("Password changed: id={}", user.getId());
    }

    @Override
    @Transactional
    public void forgotPassword(String email) {
        String normalizedEmail = email.trim().toLowerCase();
        userRepo.findByEmailIgnoreCase(normalizedEmail).ifPresent(user -> {
            if (!user.isActive()) {
                logger.info("Password reset requested for inactive user: {}", normalizedEmail);
                return;
            }

            // Genera token aleatorio de 32 bytes (64 chars hex)
            byte[] tokenBytes = new byte[TOKEN_BYTES];
            secureRandom.nextBytes(tokenBytes);
            String token = bytesToHex(tokenBytes);

            // Hash SHA-256 para almacenar
            String tokenHash = hashToken(token);

            Instant now = Instant.now();
            Instant expiresAt = now.plus(TOKEN_EXPIRY_HOURS, ChronoUnit.HOURS);

            PasswordResetToken resetToken = new PasswordResetToken();
            resetToken.setUser(user);
            resetToken.setTokenHash(tokenHash);
            resetToken.setExpiresAt(expiresAt);
            passwordResetTokenRepo.save(resetToken);

            // Construye link de reset
            String resetLink = frontendUrl + "/reset-password?token=" + token;
            String subject = "Recuperación de contraseña - Inventario QR";
            String body = """
                    Hola %s,

                    Has solicitado restablecer tu contraseña. Haz clic en el siguiente enlace para crear una nueva:

                    %s

                    Este enlace expira en 1 hora. Si no solicitaste este cambio, ignora este mensaje.

                    Saludos,
                    Equipo Inventario QR
                    """.formatted(user.getName(), resetLink);

            mailService.send(user.getEmail(), subject, body);
            logger.info("Password reset email sent to: {}", normalizedEmail);
        });
        // Si el usuario no existe, no hacemos nada (anti-enumeración)
    }

    @Override
    @Transactional
    public void resetPassword(String token, String newPassword) {
        String tokenHash = hashToken(token);

        PasswordResetToken resetToken = passwordResetTokenRepo.findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidCredentialsException("Token inválido o expirado"));

        Instant now = Instant.now();
        if (resetToken.getUsedAt() != null || resetToken.getExpiresAt().isBefore(now)) {
            throw new InvalidCredentialsException("Token inválido o expirado");
        }

        PasswordValidator.validate(newPassword);

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        user.setPasswordChangedAt(now);
        userRepo.save(user);

        // Marca token como usado
        resetToken.setUsedAt(now);
        passwordResetTokenRepo.save(resetToken);

        // Revoca todos los refresh tokens del usuario
        tokenService.revokeAllRefreshTokensForUser(user.getId());

        logger.info("Password reset completed for user: {}", user.getEmail());
    }

    // Convierte bytes a hex string (lowercase)
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    // SHA-256 hash del token
    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    // Emite access + refresh y setea la cookie httpOnly con el refresh token.
    private AuthResponse issueTokens(User user, HttpServletResponse response) {
        String accessToken = jwtUtil.generateAccessToken(user);
        String refreshToken = jwtUtil.generateRefreshToken();
        Instant refreshExpiresAt = Instant.now().plus(jwtUtil.refreshExpirationDays(), ChronoUnit.DAYS);

        tokenService.saveRefreshToken(user.getId(), refreshToken, refreshExpiresAt);
        setRefreshCookie(response, refreshToken);

        return new AuthResponse(accessToken, toResponse(user));
    }

    private void setRefreshCookie(HttpServletResponse response, String value) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(Duration.ofDays(jwtUtil.refreshExpirationDays()))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getName(),
                user.isActive(),
                user.isMustChangePassword(),
                user.getRole(),
                user.getCreatedAt());
    }
}
