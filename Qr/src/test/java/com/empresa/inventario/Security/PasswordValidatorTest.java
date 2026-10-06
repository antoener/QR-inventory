package com.empresa.inventario.Security;

import com.empresa.inventario.Exceptions.InvalidPasswordException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PasswordValidatorTest {

    @Test
    void validate_deberiaAceptarPasswordValida() {
        assertDoesNotThrow(() -> PasswordValidator.validate("Abcd1234!"));
    }

    @Test
    void validate_deberiaRechazarPasswordMuyCorta() {
        assertThrows(InvalidPasswordException.class, () -> PasswordValidator.validate("Ab1!"));
    }

    @Test
    void validate_deberiaRechazarPasswordSinMayuscula() {
        assertThrows(InvalidPasswordException.class, () -> PasswordValidator.validate("abcd1234!"));
    }

    @Test
    void validate_deberiaRechazarPasswordSinDigito() {
        assertThrows(InvalidPasswordException.class, () -> PasswordValidator.validate("Abcdefgh!"));
    }

    @Test
    void validate_deberiaRechazarPasswordSinCaracterEspecial() {
        assertThrows(InvalidPasswordException.class, () -> PasswordValidator.validate("Abcd1234"));
    }
}
