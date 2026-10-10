package com.empresa.inventario.Common;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

// Cuerpo de error estándar para todas las respuestas de error de la API.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ApiError {

    // Código HTTP (ej: 409, 400).
    private int status;

    // Mensaje humano, en español.
    private String message;

    // Campo involucrado, si aplica (ej: "username" en un 409 de duplicado). Puede ser null.
    private String field;

    // Momento del error (UTC).
    private Instant timestamp;

    // Segundos restantes hasta que se permita reintentar (rate limit 429).
    private Long retryAfter;

    // Constructor para compatibilidad hacia atrás (sin retryAfter).
    public ApiError(int status, String message, String field, Instant timestamp) {
        this(status, message, field, timestamp, null);
    }
}
