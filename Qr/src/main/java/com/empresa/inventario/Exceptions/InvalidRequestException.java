package com.empresa.inventario.Exceptions;

// Payload inválido detectado en la capa de servicio (reglas de negocio que
// no pueden expresarse con anotaciones de Bean Validation).
// El GlobalExceptionHandler la convierte en 400.
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
