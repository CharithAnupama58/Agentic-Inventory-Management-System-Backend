package com.pos.system.repository;

import com.pos.system.model.SaleItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface SaleItemRepository extends JpaRepository<SaleItem, UUID> {

    List<SaleItem> findBySaleId(UUID saleId);

    // ── Top products by net quantity sold ─────────────────────────────────────
    @Query("SELECT si.productId, " +
           "SUM(si.quantity - COALESCE(si.refundedQuantity,0)), " +
           "SUM((si.quantity - COALESCE(si.refundedQuantity,0)) * si.price) " +
           "FROM SaleItem si JOIN si.sale s " +
           "WHERE s.tenantId = :tenantId " +
           "AND s.createdAt BETWEEN :from AND :to " +
           "AND s.status != 'REFUNDED' " +
           "GROUP BY si.productId " +
           "HAVING SUM(si.quantity - COALESCE(si.refundedQuantity,0)) > 0 " +
           "ORDER BY SUM(si.quantity - COALESCE(si.refundedQuantity,0)) DESC")
    List<Object[]> findTopProductsByQuantity(
            @Param("tenantId") UUID tenantId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    // ── Full product performance with profit data ─────────────────────────────
    // Returns: productId, qty, revenue, cogs
    @Query("SELECT si.productId, " +
           "SUM(si.quantity - COALESCE(si.refundedQuantity,0)), " +
           "SUM((si.quantity - COALESCE(si.refundedQuantity,0)) * si.price), " +
           "SUM((si.quantity - COALESCE(si.refundedQuantity,0)) * p.costPrice) " +
           "FROM SaleItem si " +
           "JOIN si.sale s " +
           "JOIN Product p ON p.id = si.productId " +
           "WHERE s.tenantId = :tenantId " +
           "AND s.createdAt BETWEEN :from AND :to " +
           "AND s.status != 'REFUNDED' " +
           "GROUP BY si.productId " +
           "HAVING SUM(si.quantity - COALESCE(si.refundedQuantity,0)) > 0 " +
           "ORDER BY SUM((si.quantity - COALESCE(si.refundedQuantity,0)) * si.price) DESC")
    List<Object[]> findProductPerformance(
            @Param("tenantId") UUID tenantId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    // ── COGS for a date range ─────────────────────────────────────────────────
    @Query("SELECT COALESCE(SUM((si.quantity - COALESCE(si.refundedQuantity,0)) * p.costPrice), 0) " +
           "FROM SaleItem si JOIN si.sale s JOIN Product p ON p.id = si.productId " +
           "WHERE s.tenantId = :tenantId " +
           "AND s.createdAt BETWEEN :from AND :to " +
           "AND s.status != 'REFUNDED'")
    BigDecimal sumCostOfGoodsSold(
            @Param("tenantId") UUID tenantId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    // ── Products with zero sales in last N days (slow movers) ────────────────
    @Query("SELECT DISTINCT si.productId FROM SaleItem si " +
           "JOIN si.sale s " +
           "WHERE s.tenantId = :tenantId " +
           "AND s.createdAt >= :since " +
           "AND s.status != 'REFUNDED'")
    List<UUID> findProductsWithSalesSince(
            @Param("tenantId") UUID tenantId,
            @Param("since") LocalDateTime since);

    // ── Profit per product for margin analysis ────────────────────────────────
    @Query("SELECT si.productId, " +
           "SUM((si.quantity - COALESCE(si.refundedQuantity,0)) * si.price) AS revenue, " +
           "SUM((si.quantity - COALESCE(si.refundedQuantity,0)) * p.costPrice) AS cogs " +
           "FROM SaleItem si " +
           "JOIN si.sale s " +
           "JOIN Product p ON p.id = si.productId " +
           "WHERE s.tenantId = :tenantId " +
           "AND s.createdAt BETWEEN :from AND :to " +
           "AND s.status != 'REFUNDED' " +
           "GROUP BY si.productId " +
           "HAVING SUM((si.quantity - COALESCE(si.refundedQuantity,0)) * si.price) > 0 " +
           "ORDER BY " +
           "((SUM((si.quantity - COALESCE(si.refundedQuantity,0)) * si.price) - " +
           "SUM((si.quantity - COALESCE(si.refundedQuantity,0)) * p.costPrice)) / " +
           "NULLIF(SUM((si.quantity - COALESCE(si.refundedQuantity,0)) * si.price), 0)) DESC")
    List<Object[]> findProductProfitMargins(
            @Param("tenantId") UUID tenantId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}
