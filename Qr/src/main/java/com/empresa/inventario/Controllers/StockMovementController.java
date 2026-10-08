package com.empresa.inventario.Controllers;

import com.empresa.inventario.DTOs.Request.CreateStockMovementRequest;
import com.empresa.inventario.DTOs.Response.StockMovementResponse;
import com.empresa.inventario.Enums.MovementPeriod;
import com.empresa.inventario.Enums.MovementType;
import com.empresa.inventario.Services.IStockMovement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Endpoints REST de movimientos de stock. Sin logica: todo delega a IStockMovement.
@RestController
@RequestMapping("/api/movements")
public class StockMovementController {

    private final IStockMovement stockMovementService;

    public StockMovementController(IStockMovement stockMovementService) {
        this.stockMovementService = stockMovementService;
    }

    // Registra una entrada de stock (INBOUND). El servicio valida el motivo,
    // el origen, el producto activo y actualiza el stock cacheado.
    @PostMapping("/inbound")
    public ResponseEntity<StockMovementResponse> registerInbound(
            @Valid @RequestBody CreateStockMovementRequest request) {
        StockMovementResponse response = stockMovementService.registerInbound(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // Registra una salida de stock (OUTBOUND). El servicio valida que el origen
    // sea MANUAL, que haya stock suficiente y descuenta del producto.
    @PostMapping("/outbound")
    public ResponseEntity<StockMovementResponse> registerOutbound(
            @Valid @RequestBody CreateStockMovementRequest request) {
        StockMovementResponse response = stockMovementService.registerOutbound(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // Devuelve el stock cacheado de un producto. Incluye productos inactivos.
    // Si el producto no existe, el servicio lanza ResourceNotFoundException (404).
    @GetMapping("/stock/{productId}")
    public ResponseEntity<Integer> getCurrentStock(@PathVariable Long productId) {
        int stock = stockMovementService.getCurrentStock(productId);
        return ResponseEntity.ok(stock);
    }

    // Historial de movimientos filtrable por periodo, producto y tipo.
    // Si no se envia period se devuelve todo el historial (desde Instant.EPOCH).
    @GetMapping
    public ResponseEntity<List<StockMovementResponse>> findByPeriod(
            @RequestParam(required = false) MovementPeriod period,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) MovementType type) {
        return ResponseEntity.ok(stockMovementService.findByPeriod(period, productId, type));
    }

    // Ultimos 20 movimientos sin importar filtro.
    @GetMapping("/recent")
    public ResponseEntity<List<StockMovementResponse>> recent() {
        return ResponseEntity.ok(stockMovementService.recent());
    }
}
