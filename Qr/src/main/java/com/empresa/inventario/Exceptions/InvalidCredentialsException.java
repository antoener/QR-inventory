package com.empresa.inventario.Exceptions;

// Credenciales invalidas o sesion no valida. El GlobalExceptionHandler la
// convierte en 401. Mensaje generico para no revelar si el usuario existe.
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
