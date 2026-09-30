package com.empresa.inventario.Exceptions;

import lombok.Getter;

@Getter
public class DuplicateResourceException extends RuntimeException {

    // Campo duplicado (ej: "username", "email", "code") — lo usa el GlobalExceptionHandler para el detalle del error.
    private final String field;

    // Valor que se intentó registrar y ya existe.
    private final String value;

    public DuplicateResourceException(String field, String value) {
        super("Field '" + field + "' with value '" + value + "' is already in use");
        this.field = field;
        this.value = value;
    }
}
