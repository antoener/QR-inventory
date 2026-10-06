package com.empresa.inventario.DTOs.Response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    // Access token JWT que el cliente envia en el header Authorization.
    private String token;

    // Datos del usuario autenticado (incluye mustChangePassword).
    private UserResponse user;
}
