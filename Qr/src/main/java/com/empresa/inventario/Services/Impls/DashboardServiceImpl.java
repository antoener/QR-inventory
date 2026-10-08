package com.empresa.inventario.Services.Impls;

import com.empresa.inventario.DTOs.Response.DashboardSummaryResponse;
import com.empresa.inventario.Enums.MovementType;
import com.empresa.inventario.Repository.ProductRepo;
import com.empresa.inventario.Repository.StockMovementRepo;
import com.empresa.inventario.Services.IDashboardService;
import com.empresa.inventario.Services.IStockMovement;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements IDashboardService {

    private final ProductRepo productRepo;
    private final StockMovementRepo stockMovementRepo;
    private final IStockMovement stockMovementService;

    @Override
    @Transactional(readOnly = true)
    public DashboardSummaryResponse getSummary() {
        Instant since = LocalDate.now(ZoneId.systemDefault())
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant();

        long totalProducts = productRepo.count();
        long activeProducts = productRepo.countByActiveTrue();
        long totalStock = productRepo.sumStock();

        long inboundTodayCount = stockMovementRepo.countSince(MovementType.INBOUND, since);
        long inboundTodayUnits = stockMovementRepo.sumQuantitySince(MovementType.INBOUND, since);
        long outboundTodayCount = stockMovementRepo.countSince(MovementType.OUTBOUND, since);
        long outboundTodayUnits = stockMovementRepo.sumQuantitySince(MovementType.OUTBOUND, since);

        return new DashboardSummaryResponse(
                totalProducts,
                activeProducts,
                totalStock,
                inboundTodayCount,
                inboundTodayUnits,
                outboundTodayCount,
                outboundTodayUnits,
                stockMovementService.recent());
    }
}
