package com.empresa.inventario.Common;

import com.empresa.inventario.Exceptions.DuplicateResourceException;
import com.empresa.inventario.Exceptions.ResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;

// Manejo global de errores: convierte las excepciones de negocio en respuestas HTTP consistentes (ApiError).
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 409: recurso duplicado (username/email ya existentes).
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiError> handleDuplicateResource(DuplicateResourceException ex) {
        ApiError error = new ApiError(
                HttpStatus.CONFLICT.value(),
                ex.getMessage(),
                ex.getField(),
                Instant.now());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    // 404: recurso no encontrado (uso genérico para cualquier entidad).
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleResourceNotFound(ResourceNotFoundException ex) {
        ApiError error = new ApiError(
                HttpStatus.NOT_FOUND.value(),
                ex.getMessage(),
                null,
                Instant.now());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    // 400: payload inválido (falla la validación del DTO de request).
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        ApiError error = new ApiError(
                HttpStatus.BAD_REQUEST.value(),
                message,
                null,
                Instant.now());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
}
