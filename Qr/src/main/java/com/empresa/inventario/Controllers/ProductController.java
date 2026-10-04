package com.empresa.inventario.Controllers;
import com.empresa.inventario.DTOs.Request.CreateProductRequest;
import com.empresa.inventario.DTOs.Request.UpdateProductRequest;
import com.empresa.inventario.DTOs.Request.UpdateProductStatusRequest;
import com.empresa.inventario.DTOs.Response.ProductResponse;
import com.empresa.inventario.Services.IProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Endpoints REST de gestión de productos. Sin lógica: todo delega a IProductService.
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final IProductService productService;

    public ProductController(IProductService productService) {
        this.productService = productService;
    }

    // Alta de producto. @Valid activa la Bean Validation del DTO (400) y los
    // duplicados de código llegan como 409 desde el GlobalExceptionHandler.
    @PostMapping("/save")
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody CreateProductRequest request) {
        ProductResponse created = productService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // Búsqueda de producto por ID. Incluye productos inactivos: el front los
    // distingue por el campo active del ProductResponse. Si no existe, 404.
    @GetMapping("/id/{id}")
    public ResponseEntity<ProductResponse> findById(@PathVariable Long id) {
        ProductResponse product = productService.findById(id);
        return ResponseEntity.ok(product);
    }

    // Búsqueda exacta de producto por código. Lo llama el escaneo del QR.
    // El servicio normaliza mayúsculas/minúsculas y espacios.
    @GetMapping("/code/{code}")
    public ResponseEntity<ProductResponse> findByCode(@PathVariable String code) {
        ProductResponse product = productService.findByCode(code);
        return ResponseEntity.ok(product);
    }

    // Lista completa del catálogo o búsqueda parcial por nombre/código.
    // q vacío, ausente o solo espacios → devuelve todo el catálogo.
    @GetMapping("/list")
    public ResponseEntity<List<ProductResponse>> list(@RequestParam(required = false) String q) {
        return ResponseEntity.ok(productService.list(q));
    }

    // Actualización de nombre y descripción. No modifica el código QR, stock ni estado.
    @PutMapping("/update/{id}")
    public ResponseEntity<ProductResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProductRequest request) {
        ProductResponse updated = productService.update(id, request);
        return ResponseEntity.ok(updated);
    }

    // Activa o desactiva un producto (baja lógica). No borra nada; conserva el historial.
    // No-op si ya está en el estado solicitado.
    @PatchMapping("/{id}/status")
    public ResponseEntity<Void> setActive(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProductStatusRequest request) {
        productService.setActive(id, request.getActive());
        return ResponseEntity.noContent().build();
    }
}
