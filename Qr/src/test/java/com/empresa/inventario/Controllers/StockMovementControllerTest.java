package com.empresa.inventario.Controllers;

import com.empresa.inventario.DTOs.Request.CreateStockMovementRequest;
import com.empresa.inventario.DTOs.Response.StockMovementResponse;
import com.empresa.inventario.Enums.MovementType;
import com.empresa.inventario.Enums.Origin;
import com.empresa.inventario.Enums.Reason;
import com.empresa.inventario.Exceptions.ResourceNotFoundException;
import com.empresa.inventario.Services.IStockMovement;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StockMovementController.class)
@AutoConfigureMockMvc(addFilters = false)
class StockMovementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private IStockMovement stockMovementService;

    @Test
    void registerInbound_deberiaRetornar201() throws Exception {
        CreateStockMovementRequest request = buildRequest(Reason.PRODUCTION, Origin.MACHINE);
        StockMovementResponse response = buildResponse(1L, MovementType.INBOUND, Reason.PRODUCTION, Origin.MACHINE);

        when(stockMovementService.registerInbound(any(CreateStockMovementRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/movements/inbound")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.type", is("INBOUND")))
                .andExpect(jsonPath("$.quantity", is(5)))
                .andExpect(jsonPath("$.reason", is("PRODUCTION")))
                .andExpect(jsonPath("$.origin", is("MACHINE")));
    }

    @Test
    void registerOutbound_deberiaRetornar201() throws Exception {
        CreateStockMovementRequest request = buildRequest(Reason.SALE, Origin.MANUAL);
        StockMovementResponse response = buildResponse(2L, MovementType.OUTBOUND, Reason.SALE, Origin.MANUAL);

        when(stockMovementService.registerOutbound(any(CreateStockMovementRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/movements/outbound")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(2)))
                .andExpect(jsonPath("$.type", is("OUTBOUND")))
                .andExpect(jsonPath("$.reason", is("SALE")))
                .andExpect(jsonPath("$.origin", is("MANUAL")));
    }

    @Test
    void registerInbound_conPayloadInvalido_deberiaRetornar400() throws Exception {
        CreateStockMovementRequest request = new CreateStockMovementRequest();
        // productId, quantity y reason son null, origin tambien -> deberia fallar la validacion

        mockMvc.perform(post("/api/movements/inbound")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getCurrentStock_deberiaRetornar200ConStock() throws Exception {
        when(stockMovementService.getCurrentStock(1L)).thenReturn(42);

        mockMvc.perform(get("/api/movements/stock/1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", is(42)));
    }

    @Test
    void getCurrentStock_conProductoInexistente_deberiaRetornar404() throws Exception {
        when(stockMovementService.getCurrentStock(99L))
                .thenThrow(new ResourceNotFoundException("Product not found"));

        mockMvc.perform(get("/api/movements/stock/99")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.message", is("Product not found")));
    }

    private CreateStockMovementRequest buildRequest(Reason reason, Origin origin) {
        CreateStockMovementRequest request = new CreateStockMovementRequest();
        request.setProductId(1L);
        request.setQuantity(5);
        request.setReason(reason);
        request.setOrigin(origin);
        return request;
    }

    private StockMovementResponse buildResponse(Long id, MovementType type, Reason reason, Origin origin) {
        return new StockMovementResponse(
                id,
                1L,
                "BOL-001",
                "Bolso cuero",
                type,
                5,
                reason,
                null,
                origin,
                1L,
                "Admin User",
                Instant.now());
    }
}
