package com.empresa.inventario.Services.Impls;

import com.empresa.inventario.DTOs.Request.ChangePasswordRequest;
import com.empresa.inventario.DTOs.Request.LoginRequest;
import com.empresa.inventario.DTOs.Response.AuthResponse;
import com.empresa.inventario.DTOs.Response.UserResponse;
import com.empresa.inventario.Exceptions.InvalidCredentialsException;
import com.empresa.inventario.Model.RefreshToken;
import com.empresa.inventario.Model.User;
import com.empresa.inventario.Repository.UserRepo;
import com.empresa.inventario.Security.JwtUtil;
import com.empresa.inventario.Security.PasswordValidator;
import com.empresa.inventario.Security.SecurityContextService;
import com.empresa.inventario.Security.TokenService;
import com.empresa.inventario.Services.IAuthService;
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

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class AuthServImpl implements IAuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthServImpl.class);
    private static final String REFRESH_COOKIE = "refresh_token";
    private static final String COOKIE_PATH = "/api/auth";

    private final UserRepo userRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final TokenService tokenService;
    private final SecurityContextService securityContextService;
    private final boolean cookieSecure;

    public AuthServImpl(
            UserRepo userRepo,
            PasswordEncoder passwordEncoder,
            JwtUtil jwtUtil,
            TokenService tokenService,
            SecurityContextService securityContextService,
            @Value("${app.security.cookie-secure:true}") boolean cookieSecure) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.tokenService = tokenService;
        this.securityContextService = securityContextService;
        this.cookieSecure = cookieSecure;
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request, HttpServletResponse response) {
        // Resuelve el identifier contra username o email (mensaje generico anti-enumeracion).
        User user = userRepo.findByUsernameIgnoreCase(request.getIdentifier().trim())
                .or(() -> userRepo.findByEmailIgnoreCase(request.getIdentifier().trim()))
                .orElseThrow(() -> new InvalidCredentialsException("Credenciales invalidas"));

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
