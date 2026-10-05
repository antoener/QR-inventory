package com.empresa.inventario.DTOs.Response;

import com.empresa.inventario.Enums.MovementType;
import com.empresa.inventario.Enums.Origin;
import com.empresa.inventario.Enums.Reason;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StockMovementResponse {

    private Long id;

    private Long productId;

    private String productCode;

    private String productName;

    private MovementType type;

    private Integer quantity;

    private Reason reason;

    private String detail;

    private Origin origin;

    private Long userId;

    private String userName;

    private Instant createdAt;
}
