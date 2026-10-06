package com.empresa.inventario.DTOs.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class LoginRequest {

    // Username o email: el servicio resuelve contra cualquiera de los dos.
    @NotBlank
    @Size(max = 100)
    private String identifier;

    @NotBlank
    @Size(max = 100)
    private String password;
}
