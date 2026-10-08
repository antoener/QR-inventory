package com.empresa.inventario.Controllers;

import com.empresa.inventario.DTOs.Response.DashboardSummaryResponse;
import com.empresa.inventario.DTOs.Response.StockMovementResponse;
import com.empresa.inventario.Enums.MovementType;
import com.empresa.inventario.Enums.Origin;
import com.empresa.inventario.Enums.Reason;
import com.empresa.inventario.Services.IDashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardController.class)
@AutoConfigureMockMvc(addFilters = false)
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IDashboardService dashboardService;

    @Test
    void summary_deberiaRetornar200ConMetricas() throws Exception {
        StockMovementResponse recent = new StockMovementResponse(
                1L,
                1L,
                "BOL-001",
                "Bolso cuero",
                MovementType.INBOUND,
                5000,
                Reason.PRODUCTION,
                null,
                Origin.MACHINE,
                1L,
                "Admin",
                Instant.now());

        DashboardSummaryResponse summary = new DashboardSummaryResponse(
                80L,
                75L,
                120000L,
                5L,
                25000L,
                2L,
                3000L,
                List.of(recent));

        when(dashboardService.getSummary()).thenReturn(summary);

        mockMvc.perform(get("/api/dashboard/summary")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProducts", is(80)))
                .andExpect(jsonPath("$.activeProducts", is(75)))
                .andExpect(jsonPath("$.totalStock", is(120000)))
                .andExpect(jsonPath("$.inboundTodayCount", is(5)))
                .andExpect(jsonPath("$.inboundTodayUnits", is(25000)))
                .andExpect(jsonPath("$.outboundTodayCount", is(2)))
                .andExpect(jsonPath("$.outboundTodayUnits", is(3000)))
                .andExpect(jsonPath("$.recentMovements[0].id", is(1)));
    }
}
