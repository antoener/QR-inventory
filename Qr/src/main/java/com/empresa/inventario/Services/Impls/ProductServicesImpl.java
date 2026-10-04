package com.empresa.inventario.Services.Impls;

import com.empresa.inventario.DTOs.Request.CreateProductRequest;
import com.empresa.inventario.DTOs.Request.UpdateProductRequest;
import com.empresa.inventario.DTOs.Response.ProductResponse;
import com.empresa.inventario.Exceptions.DuplicateResourceException;
import com.empresa.inventario.Exceptions.ResourceNotFoundException;
import com.empresa.inventario.Model.Product;
import com.empresa.inventario.Repository.ProductRepo;
import com.empresa.inventario.Services.IProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductServicesImpl implements IProductService {

    private static final Logger logger = LoggerFactory.getLogger(ProductServicesImpl.class);

    private final ProductRepo productRepo;

    public ProductServicesImpl(ProductRepo productRepo) {
        this.productRepo = productRepo;
    }

    @Override
    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        String code = normalizeCode(request.getCode());
        String name = normalizeName(request.getName());
        String description = normalizeDescription(request.getDescription());

        // El código es único en el sistema y se imprime en el QR del estante.
        if (productRepo.existsByCodeIgnoreCase(code)) {
            throw new DuplicateResourceException("code", code);
        }

        Product product = new Product();
        product.setCode(code);
        product.setName(name);
        product.setDescription(description);
        // stock queda en 0 (default del primitivo) y active en true (default de la entidad).

        Product saved = productRepo.save(product);
        logger.info("Product created: id={}, code={}", saved.getId(), saved.getCode());

        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse findById(Long id) {
        Product product = productRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Resource not found"));
        logger.info("Product consulted: id={}, code={}", product.getId(), product.getCode());
        return toResponse(product);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse findByCode(String code) {
        String normalizedCode = normalizeCode(code);
        Product product = productRepo.findByCodeIgnoreCase(normalizedCode)
                .orElseThrow(() -> new ResourceNotFoundException("Resource not found"));
        logger.info("Product consulted by code: id={}, code={}", product.getId(), product.getCode());
        return toResponse(product);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> list(String query) {
        String q = (query == null) ? "" : query.trim();
        if (q.isEmpty()) {
            return productRepo.findAllByOrderByIdAsc().stream()
                    .map(this::toResponse)
                    .toList();
        }
        return productRepo.searchByCodeOrName(q).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public ProductResponse update(Long id, UpdateProductRequest request) {
        Product product = productRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Resource not found"));

        product.setName(normalizeName(request.getName()));
        product.setDescription(normalizeDescription(request.getDescription()));
        // No se modifica el código QR, el stock, el estado ni la fecha de creación.

        Product saved = productRepo.save(product);
        logger.info("Product updated: id={}, code={}", saved.getId(), saved.getCode());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void setActive(Long id, boolean active) {
        Product product = productRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Resource not found"));

        // Idempotente: si ya está en el estado solicitado, no se guarda de nuevo.
        if (product.isActive() == active) {
            return;
        }

        product.setActive(active);
        Product saved = productRepo.save(product);
        logger.info("Product status updated: id={}, code={}, active={}", saved.getId(), saved.getCode(), saved.isActive());
    }

    private String normalizeCode(String code) {
        if (code == null) {
            return null;
        }
        return code.trim().toUpperCase();
    }

    private String normalizeName(String name) {
        if (name == null) {
            return null;
        }
        return name.trim();
    }

    private String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }
        String trimmed = description.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getCode(),
                product.getName(),
                product.getDescription(),
                product.isActive(),
                product.getCreatedAt());
    }
}
