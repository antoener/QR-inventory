package com.empresa.inventario.DTOs.Response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummaryResponse {

    private long totalProducts;

    private long activeProducts;

    private long totalStock;

    private long inboundTodayCount;

    private long inboundTodayUnits;

    private long outboundTodayCount;

    private long outboundTodayUnits;

    private List<StockMovementResponse> recentMovements;
}
