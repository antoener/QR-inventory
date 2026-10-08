package com.empresa.inventario.Repository;

import com.empresa.inventario.Enums.MovementType;
import com.empresa.inventario.Model.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface StockMovementRepo extends JpaRepository<StockMovement, Long> {

    // Ultimos movimientos sin importar filtro (para dashboard y pantalla rapida).
    List<StockMovement> findTop20ByOrderByCreatedAtDesc();

    // Historial filtrable por producto, tipo y fecha de inicio. Los parametros
    // nulos no aplican filtro (se usa en el caso "sin period" para todo el historial).
    @Query("""
            SELECT m FROM StockMovement m
            WHERE (:productId IS NULL OR m.product.id = :productId)
              AND (:type IS NULL OR m.type = :type)
              AND m.createdAt >= :since
            ORDER BY m.createdAt DESC
            """)
    List<StockMovement> findByFilters(
            @Param("since") Instant since,
            @Param("productId") Long productId,
            @Param("type") MovementType type);

    // Metricas del dashboard (movimientos de hoy por tipo).
    @Query("SELECT COUNT(m) FROM StockMovement m WHERE m.type = :type AND m.createdAt >= :since")
    long countSince(@Param("type") MovementType type, @Param("since") Instant since);

    @Query("SELECT COALESCE(SUM(m.quantity), 0) FROM StockMovement m WHERE m.type = :type AND m.createdAt >= :since")
    long sumQuantitySince(@Param("type") MovementType type, @Param("since") Instant since);
}
