package com.empresa.inventario.Services;

import com.empresa.inventario.DTOs.Request.CreateStockMovementRequest;
import com.empresa.inventario.DTOs.Response.StockMovementResponse;
import com.empresa.inventario.Enums.MovementPeriod;
import com.empresa.inventario.Enums.MovementType;

import java.util.List;

public interface IStockMovement {

    StockMovementResponse registerInbound(CreateStockMovementRequest request);

    StockMovementResponse registerOutbound(CreateStockMovementRequest request);

    int getCurrentStock(Long productId);

    List<StockMovementResponse> findByPeriod(MovementPeriod period, Long productId, MovementType type);

    List<StockMovementResponse> recent();
}
