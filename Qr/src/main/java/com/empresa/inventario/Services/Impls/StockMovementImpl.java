package com.empresa.inventario.Services.Impls;
import com.empresa.inventario.DTOs.Request.CreateStockMovementRequest;
import com.empresa.inventario.DTOs.Response.StockMovementResponse;
import com.empresa.inventario.Enums.MovementPeriod;
import com.empresa.inventario.Enums.MovementType;
import com.empresa.inventario.Enums.Origin;
import com.empresa.inventario.Enums.Reason;
import com.empresa.inventario.Exceptions.BusinessRuleException;
import com.empresa.inventario.Exceptions.InvalidCredentialsException;
import com.empresa.inventario.Exceptions.InvalidRequestException;
import com.empresa.inventario.Exceptions.ResourceNotFoundException;
import com.empresa.inventario.Model.Product;
import com.empresa.inventario.Model.StockMovement;
import com.empresa.inventario.Model.User;
import com.empresa.inventario.Repository.ProductRepo;
import com.empresa.inventario.Repository.StockMovementRepo;
import com.empresa.inventario.Security.SecurityContextService;
import com.empresa.inventario.Services.IStockMovement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;


@Service
public class StockMovementImpl implements IStockMovement {

    private static final Logger logger = LoggerFactory.getLogger(StockMovementImpl.class);

    private final StockMovementRepo stockMovementRepo;
    private final ProductRepo productRepo;
    private final SecurityContextService securityContextService;

    public StockMovementImpl(StockMovementRepo stockMovementRepo,
                             ProductRepo productRepo,
                             SecurityContextService securityContextService) {
        this.stockMovementRepo = stockMovementRepo;
        this.productRepo = productRepo;
        this.securityContextService = securityContextService;
    }

    @Override
    @Transactional
    public StockMovementResponse registerInbound(CreateStockMovementRequest request) {
        User user = requireAuthenticatedUser();
        Product product = findActiveProduct(request.getProductId());

        Reason reason = request.getReason();
        if (!reason.isInbound()) {
            throw new InvalidRequestException(
                    "Reason '" + reason + "' is not valid for an inbound movement");
        }

        Origin origin = request.getOrigin();
        if (origin == null) {
            // Defensa en profundidad: el controller debería haber rechazado esto con 400.
            throw new InvalidRequestException("Origin is required");
        }

        String detail = normalizeDetail(request.getDetail());

        int quantity = request.getQuantity();
        int newStock = safeAdd(product.getStock(), quantity);

        product.setStock(newStock);

        StockMovement movement = new StockMovement();
        movement.setProduct(product);
        movement.setType(MovementType.INBOUND);
        movement.setQuantity(quantity);
        movement.setReason(reason);
        movement.setDetail(detail);
        movement.setOrigin(origin);
        movement.setUser(user);

        Product savedProduct = productRepo.save(product);
        StockMovement savedMovement = stockMovementRepo.save(movement);

        logger.info("Inbound movement registered: id={}, productId={}, quantity={}, origin={}, userId={}",
                savedMovement.getId(), savedProduct.getId(), quantity, origin, user.getId());

        return toResponse(savedMovement);
    }

    @Override
    @Transactional
    public StockMovementResponse registerOutbound(CreateStockMovementRequest request) {
        User user = requireAuthenticatedUser();
        Product product = findActiveProduct(request.getProductId());

        Reason reason = request.getReason();
        if (!reason.isOutbound()) {
            throw new InvalidRequestException(
                    "Reason '" + reason + "' is not valid for an outbound movement");
        }

        Origin origin = request.getOrigin();
        if (origin != Origin.MANUAL) {
            throw new InvalidRequestException("Outbound movements must have origin MANUAL");
        }

        String detail = normalizeDetail(request.getDetail());

        int quantity = request.getQuantity();
        int currentStock = product.getStock();
        if (quantity > currentStock) {
            throw new BusinessRuleException(
                    "Insufficient stock. Available: " + currentStock + ", requested: " + quantity);
        }

        product.setStock(currentStock - quantity);

        StockMovement movement = new StockMovement();
        movement.setProduct(product);
        movement.setType(MovementType.OUTBOUND);
        movement.setQuantity(quantity);
        movement.setReason(reason);
        movement.setDetail(detail);
        movement.setOrigin(origin);
        movement.setUser(user);

        Product savedProduct = productRepo.save(product);
        StockMovement savedMovement = stockMovementRepo.save(movement);

        logger.info("Outbound movement registered: id={}, productId={}, quantity={}, origin={}, userId={}",
                savedMovement.getId(), savedProduct.getId(), quantity, origin, user.getId());

        return toResponse(savedMovement);
    }

    @Override
    @Transactional(readOnly = true)
    public int getCurrentStock(Long productId) {
        Product product = productRepo.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        logger.info("Current stock consulted: productId={}, stock={}", product.getId(), product.getStock());

        return product.getStock();
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockMovementResponse> findByPeriod(MovementPeriod period, Long productId, MovementType type) {
        Instant since = period != null ? calculateCutoff(period) : Instant.EPOCH;

        return stockMovementRepo.findByFilters(since, productId, type).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockMovementResponse> recent() {
        return stockMovementRepo.findTop20ByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    private Instant calculateCutoff(MovementPeriod period) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);

        LocalDate start = switch (period) {
            case TODAY -> today;
            case THIS_WEEK -> today.with(DayOfWeek.MONDAY);
            case THIS_MONTH -> today.withDayOfMonth(1);
        };

        return start.atStartOfDay(zone).toInstant();
    }

    private User requireAuthenticatedUser() {
        Optional<User> currentUser = securityContextService.getCurrentUser();
        if (currentUser.isEmpty() || !currentUser.get().isActive()) {
            // El filtro JWT ya debería haber rechazado usuarios inactivos, pero se mantiene
            // la validación como defensa en profundidad.
            throw new InvalidCredentialsException("Invalid session");
        }
        return currentUser.get();
    }

    private Product findActiveProduct(Long productId) {
        Product product = productRepo.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        if (!product.isActive()) {
            throw new BusinessRuleException("Cannot register movement for an inactive product");
        }
        return product;
    }

    private int safeAdd(int currentStock, int quantity) {
        try {
            return Math.addExact(currentStock, quantity);
        } catch (ArithmeticException ex) {
            throw new BusinessRuleException("Movement exceeds maximum stock capacity");
        }
    }

    private String normalizeDetail(String detail) {
        if (detail == null) {
            return null;
        }
        String trimmed = detail.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > 255) {
            // Redundante con @Size del controller, pero el servicio no asume que el
            // DTO siempre pase por validación de Bean Validation.
            throw new InvalidRequestException("Detail must not exceed 255 characters");
        }
        if (containsHtml(trimmed)) {
            throw new InvalidRequestException("Detail contains invalid characters");
        }
        return trimmed;
    }

    private boolean containsHtml(String value) {
        return value.contains("<") || value.contains(">");
    }

    private StockMovementResponse toResponse(StockMovement movement) {
        Product product = movement.getProduct();
        User user = movement.getUser();
        return new StockMovementResponse(
                movement.getId(),
                product.getId(),
                product.getCode(),
                product.getName(),
                movement.getType(),
                movement.getQuantity(),
                movement.getReason(),
                movement.getDetail(),
                movement.getOrigin(),
                user.getId(),
                user.getName(),
                movement.getCreatedAt());
    }
}
