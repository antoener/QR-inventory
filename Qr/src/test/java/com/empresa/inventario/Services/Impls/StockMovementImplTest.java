package com.empresa.inventario.Services.Impls;

import com.empresa.inventario.DTOs.Request.CreateStockMovementRequest;
import com.empresa.inventario.DTOs.Response.StockMovementResponse;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockMovementImplTest {

    @Mock
    private StockMovementRepo stockMovementRepo;

    @Mock
    private ProductRepo productRepo;

    @Mock
    private SecurityContextService securityContextService;

    @InjectMocks
    private StockMovementImpl stockMovementService;

    private User user;
    private Product product;
    private CreateStockMovementRequest request;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setUsername("admin");
        user.setName("Admin User");
        user.setActive(true);

        product = new Product();
        product.setId(1L);
        product.setCode("BOL-001");
        product.setName("Bolso cuero");
        product.setStock(10);
        product.setActive(true);
        product.setVersion(1L);

        request = new CreateStockMovementRequest();
        request.setProductId(1L);
        request.setQuantity(5);
        request.setReason(Reason.PRODUCTION);
        request.setOrigin(Origin.MACHINE);
    }

    @Test
    void registerInbound_deberiaSumarStockYRegistrarMovimiento() {
        when(securityContextService.getCurrentUser()).thenReturn(Optional.of(user));
        when(productRepo.findById(1L)).thenReturn(Optional.of(product));
        when(productRepo.save(product)).thenReturn(product);
        when(stockMovementRepo.save(any(StockMovement.class))).thenAnswer(invocation -> {
            StockMovement m = invocation.getArgument(0);
            m.setId(100L);
            return m;
        });

        StockMovementResponse response = stockMovementService.registerInbound(request);

        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getProductId()).isEqualTo(1L);
        assertThat(response.getProductCode()).isEqualTo("BOL-001");
        assertThat(response.getProductName()).isEqualTo("Bolso cuero");
        assertThat(response.getType()).isEqualTo(MovementType.INBOUND);
        assertThat(response.getQuantity()).isEqualTo(5);
        assertThat(response.getReason()).isEqualTo(Reason.PRODUCTION);
        assertThat(response.getOrigin()).isEqualTo(Origin.MACHINE);
        assertThat(response.getUserId()).isEqualTo(1L);
        assertThat(response.getUserName()).isEqualTo("Admin User");

        assertThat(product.getStock()).isEqualTo(15);

        ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepo).save(captor.capture());
        StockMovement saved = captor.getValue();
        assertThat(saved.getProduct()).isEqualTo(product);
        assertThat(saved.getType()).isEqualTo(MovementType.INBOUND);
        assertThat(saved.getQuantity()).isEqualTo(5);
        assertThat(saved.getReason()).isEqualTo(Reason.PRODUCTION);
        assertThat(saved.getOrigin()).isEqualTo(Origin.MACHINE);
        assertThat(saved.getUser()).isEqualTo(user);
    }

    @Test
    void registerInbound_conOriginManual_deberiaRegistrarMovimiento() {
        request.setReason(Reason.CUSTOMER_RETURN);
        request.setOrigin(Origin.MANUAL);

        when(securityContextService.getCurrentUser()).thenReturn(Optional.of(user));
        when(productRepo.findById(1L)).thenReturn(Optional.of(product));
        when(productRepo.save(product)).thenReturn(product);
        when(stockMovementRepo.save(any(StockMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StockMovementResponse response = stockMovementService.registerInbound(request);

        assertThat(response.getOrigin()).isEqualTo(Origin.MANUAL);
        assertThat(product.getStock()).isEqualTo(15);
    }

    @Test
    void registerInbound_sinUsuarioAutenticado_deberiaLanzar401() {
        when(securityContextService.getCurrentUser()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> stockMovementService.registerInbound(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("Invalid session");

        verify(productRepo, never()).findById(any());
        verify(stockMovementRepo, never()).save(any());
    }

    @Test
    void registerInbound_conUsuarioInactivo_deberiaLanzar401() {
        user.setActive(false);
        when(securityContextService.getCurrentUser()).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> stockMovementService.registerInbound(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("Invalid session");

        verify(productRepo, never()).findById(any());
    }

    @Test
    void registerInbound_conProductoInexistente_deberiaLanzar404() {
        when(securityContextService.getCurrentUser()).thenReturn(Optional.of(user));
        when(productRepo.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> stockMovementService.registerInbound(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Product not found");

        verify(stockMovementRepo, never()).save(any());
    }

    @Test
    void registerInbound_conProductoInactivo_deberiaLanzar409() {
        product.setActive(false);
        when(securityContextService.getCurrentUser()).thenReturn(Optional.of(user));
        when(productRepo.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> stockMovementService.registerInbound(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("inactive product");

        verify(productRepo, never()).save(any());
        verify(stockMovementRepo, never()).save(any());
    }

    @Test
    void registerInbound_conRazonDeSalida_deberiaLanzar400() {
        request.setReason(Reason.SALE);

        when(securityContextService.getCurrentUser()).thenReturn(Optional.of(user));
        when(productRepo.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> stockMovementService.registerInbound(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("not valid for an inbound movement");

        verify(productRepo, never()).save(any());
        verify(stockMovementRepo, never()).save(any());
    }

    @Test
    void registerInbound_conDetailEnBlanco_deberiaGuardarNull() {
        request.setDetail("   ");

        when(securityContextService.getCurrentUser()).thenReturn(Optional.of(user));
        when(productRepo.findById(1L)).thenReturn(Optional.of(product));
        when(productRepo.save(product)).thenReturn(product);
        when(stockMovementRepo.save(any(StockMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StockMovementResponse response = stockMovementService.registerInbound(request);

        assertThat(response.getDetail()).isNull();
    }

    @Test
    void registerInbound_conDetailConHtml_deberiaLanzar400() {
        request.setDetail("<script>alert(1)</script>");

        when(securityContextService.getCurrentUser()).thenReturn(Optional.of(user));
        when(productRepo.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> stockMovementService.registerInbound(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("invalid characters");

        verify(stockMovementRepo, never()).save(any());
    }

    @Test
    void registerInbound_conOverflow_deberiaLanzar409() {
        product.setStock(Integer.MAX_VALUE);

        when(securityContextService.getCurrentUser()).thenReturn(Optional.of(user));
        when(productRepo.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> stockMovementService.registerInbound(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("maximum stock capacity");

        verify(productRepo, never()).save(any());
        verify(stockMovementRepo, never()).save(any());
    }

    @Test
    void registerOutbound_deberiaRestarStockYRegistrarMovimiento() {
        request.setReason(Reason.SALE);
        request.setOrigin(Origin.MANUAL);

        when(securityContextService.getCurrentUser()).thenReturn(Optional.of(user));
        when(productRepo.findById(1L)).thenReturn(Optional.of(product));
        when(productRepo.save(product)).thenReturn(product);
        when(stockMovementRepo.save(any(StockMovement.class))).thenAnswer(invocation -> {
            StockMovement m = invocation.getArgument(0);
            m.setId(101L);
            return m;
        });

        StockMovementResponse response = stockMovementService.registerOutbound(request);

        assertThat(response.getId()).isEqualTo(101L);
        assertThat(response.getType()).isEqualTo(MovementType.OUTBOUND);
        assertThat(response.getQuantity()).isEqualTo(5);
        assertThat(response.getReason()).isEqualTo(Reason.SALE);
        assertThat(response.getOrigin()).isEqualTo(Origin.MANUAL);
        assertThat(product.getStock()).isEqualTo(5);

        ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepo).save(captor.capture());
        StockMovement saved = captor.getValue();
        assertThat(saved.getType()).isEqualTo(MovementType.OUTBOUND);
        assertThat(saved.getOrigin()).isEqualTo(Origin.MANUAL);
    }

    @Test
    void registerOutbound_conStockQueLlegaACero_deberiaPermitirlo() {
        request.setQuantity(10);
        request.setReason(Reason.WASTE);
        request.setOrigin(Origin.MANUAL);

        when(securityContextService.getCurrentUser()).thenReturn(Optional.of(user));
        when(productRepo.findById(1L)).thenReturn(Optional.of(product));
        when(productRepo.save(product)).thenReturn(product);
        when(stockMovementRepo.save(any(StockMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StockMovementResponse response = stockMovementService.registerOutbound(request);

        assertThat(response.getQuantity()).isEqualTo(10);
        assertThat(product.getStock()).isEqualTo(0);
    }

    @Test
    void registerOutbound_conStockInsuficiente_deberiaLanzar409() {
        request.setQuantity(20);
        request.setReason(Reason.SALE);
        request.setOrigin(Origin.MANUAL);

        when(securityContextService.getCurrentUser()).thenReturn(Optional.of(user));
        when(productRepo.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> stockMovementService.registerOutbound(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Insufficient stock");

        verify(productRepo, never()).save(any());
        verify(stockMovementRepo, never()).save(any());
    }

    @Test
    void registerOutbound_conRazonDeEntrada_deberiaLanzar400() {
        request.setReason(Reason.PRODUCTION);
        request.setOrigin(Origin.MANUAL);

        when(securityContextService.getCurrentUser()).thenReturn(Optional.of(user));
        when(productRepo.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> stockMovementService.registerOutbound(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("not valid for an outbound movement");

        verify(productRepo, never()).save(any());
        verify(stockMovementRepo, never()).save(any());
    }

    @Test
    void registerOutbound_conOriginMachine_deberiaLanzar400() {
        request.setReason(Reason.SALE);
        request.setOrigin(Origin.MACHINE);

        when(securityContextService.getCurrentUser()).thenReturn(Optional.of(user));
        when(productRepo.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> stockMovementService.registerOutbound(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("must have origin MANUAL");

        verify(productRepo, never()).save(any());
        verify(stockMovementRepo, never()).save(any());
    }

    @Test
    void getCurrentStock_deberiaDevolverElStockCacheadoDelProducto() {
        when(productRepo.findById(1L)).thenReturn(Optional.of(product));

        int stock = stockMovementService.getCurrentStock(1L);

        assertThat(stock).isEqualTo(10);
        verify(productRepo).findById(1L);
    }

    @Test
    void getCurrentStock_conProductoInactivo_deberiaDevolverSuStock() {
        product.setActive(false);
        when(productRepo.findById(1L)).thenReturn(Optional.of(product));

        int stock = stockMovementService.getCurrentStock(1L);

        assertThat(stock).isEqualTo(10);
    }

    @Test
    void getCurrentStock_conProductoInexistente_deberiaLanzar404() {
        when(productRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> stockMovementService.getCurrentStock(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Product not found");
    }
}
