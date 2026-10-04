package com.empresa.inventario.DTOs.Request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateProductStatusRequest {

    @NotNull
    private Boolean active;
}
