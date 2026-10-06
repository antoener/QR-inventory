package com.empresa.inventario.Exceptions;

// Violacion de la politica de complejidad de contrasenas. El GlobalExceptionHandler
// la convierte en 400 Bad Request con field "password".
public class InvalidPasswordException extends RuntimeException {

    public InvalidPasswordException(String message) {
        super(message);
    }
}
