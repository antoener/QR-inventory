package com.empresa.inventario.Services;

import com.empresa.inventario.DTOs.Request.ChangePasswordRequest;
import com.empresa.inventario.DTOs.Request.LoginRequest;
import com.empresa.inventario.DTOs.Response.AuthResponse;
import com.empresa.inventario.DTOs.Response.UserResponse;
import jakarta.servlet.http.HttpServletResponse;

public interface IAuthService {

    // Login por username o email. Devuelve el access token + usuario y setea la
    // cookie httpOnly con el refresh token.
    AuthResponse login(LoginRequest request, HttpServletResponse response);

    // Rotacion del refresh token. Si el token ya fue rotado (reuso), revoca toda
    // la familia y devuelve 401.
    AuthResponse refresh(String refreshToken, HttpServletResponse response);

    // Logout: blacklistea el access token (jti) y revoca el refresh token.
    void logout(String accessToken, String refreshToken, HttpServletResponse response);

    // Usuario autenticado actual.
    UserResponse me();

    // Cambia la contrasena del usuario autenticado. Invalida todos los tokens
    // previos (claim pwdAt) y revoca todos los refresh tokens.
    void changePassword(ChangePasswordRequest request);

    // Inicia el flujo de recuperacion de contrasena. Envio (o loguea) un link
    // con token de un solo uso. La respuesta es siempre generica.
    void forgotPassword(String email);

    // Resetea la contrasena usando un token valido, no expirado y no usado.
    // Revoca todos los refresh tokens del usuario.
    void resetPassword(String token, String newPassword);
}
