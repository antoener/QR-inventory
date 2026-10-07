package com.empresa.inventario.DTOs.Request;

import com.empresa.inventario.Enums.Origin;
import com.empresa.inventario.Enums.Reason;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CreateStockMovementRequest {

    @NotNull
    private Long productId;

    @NotNull
    @Min(1)
    private Integer quantity;

    @NotNull
    private Reason reason;

    @Size(max = 255)
    private String detail;

    @NotNull
    private Origin origin;
}
