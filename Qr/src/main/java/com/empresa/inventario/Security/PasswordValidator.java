package com.empresa.inventario.Security;

import com.empresa.inventario.Exceptions.InvalidPasswordException;

// Politica de contrasenas: longitud 8..72 (72 = limite de BCrypt), al menos una
// mayuscula, una minuscula, un digito y un caracter especial. Aplicada al crear
// usuario y al cambiar contrasena.
public final class PasswordValidator {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 72;

    private PasswordValidator() {
    }

    public static void validate(String password) {
        if (password == null || password.isBlank()) {
            throw new InvalidPasswordException("La contrasena es obligatoria");
        }
        if (password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            throw new InvalidPasswordException("La contrasena debe tener entre 8 y 72 caracteres");
        }
        if (!password.matches(".*[A-Z].*")) {
            throw new InvalidPasswordException("La contrasena debe contener al menos una mayuscula");
        }
        if (!password.matches(".*[a-z].*")) {
            throw new InvalidPasswordException("La contrasena debe contener al menos una minuscula");
        }
        if (!password.matches(".*[0-9].*")) {
            throw new InvalidPasswordException("La contrasena debe contener al menos un digito");
        }
        if (!password.matches(".*[^A-Za-z0-9].*")) {
            throw new InvalidPasswordException("La contrasena debe contener al menos un caracter especial");
        }
    }
}
