package com.empresa.inventario.Controllers;

import com.empresa.inventario.DTOs.Request.ChangePasswordRequest;
import com.empresa.inventario.DTOs.Request.LoginRequest;
import com.empresa.inventario.DTOs.Response.AuthResponse;
import com.empresa.inventario.DTOs.Response.UserResponse;
import com.empresa.inventario.Services.IAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Endpoints de autenticacion. Sin logica: todo delega a IAuthService.
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final IAuthService authService;

    public AuthController(IAuthService authService) {
        this.authService = authService;
    }

    // Login por username o email. Setea la cookie httpOnly con el refresh token.
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {
        AuthResponse auth = authService.login(request, response);
        return ResponseEntity.ok(auth);
    }

    // Rotacion del refresh token (lee la cookie httpOnly).
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = "refresh_token", required = false) String refreshToken,
            HttpServletResponse response) {
        AuthResponse auth = authService.refresh(refreshToken, response);
        return ResponseEntity.ok(auth);
    }

    // Logout: blacklistea el access token y revoca el refresh token.
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest request,
            @CookieValue(name = "refresh_token", required = false) String refreshToken,
            HttpServletResponse response) {
        authService.logout(request.getHeader("Authorization"), refreshToken, response);
        return ResponseEntity.noContent().build();
    }

    // Usuario autenticado actual.
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me() {
        return ResponseEntity.ok(authService.me());
    }

    // Cambio de contrasena del usuario autenticado.
    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return ResponseEntity.noContent().build();
    }
}
