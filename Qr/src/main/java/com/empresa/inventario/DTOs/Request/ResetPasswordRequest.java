package com.empresa.inventario.DTOs.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ResetPasswordRequest {

    @NotBlank(message = "El token es obligatorio")
    private String token;

    @NotBlank(message = "La contrasena es obligatoria")
    @Size(min = 8, max = 72, message = "La contrasena debe tener entre 8 y 72 caracteres")
    @Pattern(regexp = ".*[A-Z].*", message = "La contrasena debe contener al menos una mayuscula")
    @Pattern(regexp = ".*[a-z].*", message = "La contrasena debe contener al menos una minuscula")
    @Pattern(regexp = ".*[0-9].*", message = "La contrasena debe contener al menos un digito")
    @Pattern(regexp = ".*[^A-Za-z0-9].*", message = "La contrasena debe contener al menos un caracter especial")
    private String newPassword;
}
