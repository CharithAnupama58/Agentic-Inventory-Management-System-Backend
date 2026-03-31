package com.pos.system.repository;

import com.pos.system.model.Sale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SaleRepository extends JpaRepository<Sale, UUID> {

    List<Sale> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    Optional<Sale> findByIdAndTenantId(UUID id, UUID tenantId);
    List<Sale> findAllByTenantIdAndCreatedAtBetween(
            UUID tenantId, LocalDateTime from, LocalDateTime to);

    // ── Gross revenue (exclude fully refunded) ────────────────────────────────
    @Query("SELECT COALESCE(SUM(s.totalAmount), 0) FROM Sale s " +
           "WHERE s.tenantId = :tenantId " +
           "AND s.createdAt BETWEEN :from AND :to " +
           "AND s.status != 'REFUNDED'")
    BigDecimal sumRevenueByTenantAndDateRange(
            @Param("tenantId") UUID tenantId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    // ── Net revenue (gross - refunded portions) ───────────────────────────────
    @Query("SELECT COALESCE(SUM(s.totalAmount - COALESCE(s.totalRefundedAmount,0)), 0) " +
           "FROM Sale s " +
           "WHERE s.tenantId = :tenantId " +
           "AND s.createdAt BETWEEN :from AND :to " +
           "AND s.status != 'REFUNDED'")
    BigDecimal sumNetRevenueByTenantAndDateRange(
            @Param("tenantId") UUID tenantId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    // ── Total refunded amount ─────────────────────────────────────────────────
    @Query("SELECT COALESCE(SUM(s.totalRefundedAmount), 0) FROM Sale s " +
           "WHERE s.tenantId = :tenantId " +
           "AND s.createdAt BETWEEN :from AND :to " +
           "AND s.status IN ('REFUNDED', 'PARTIALLY_REFUNDED')")
    BigDecimal sumRefundedByTenantAndDateRange(
            @Param("tenantId") UUID tenantId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    // ── Transaction count ─────────────────────────────────────────────────────
    @Query("SELECT COUNT(s) FROM Sale s " +
           "WHERE s.tenantId = :tenantId " +
           "AND s.createdAt BETWEEN :from AND :to " +
           "AND s.status != 'REFUNDED'")
    Long countByTenantAndDateRange(
            @Param("tenantId") UUID tenantId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    // ── Refund count ──────────────────────────────────────────────────────────
    @Query("SELECT COUNT(s) FROM Sale s " +
           "WHERE s.tenantId = :tenantId " +
           "AND s.createdAt BETWEEN :from AND :to " +
           "AND s.status IN ('REFUNDED','PARTIALLY_REFUNDED')")
    Long countRefundsByTenantAndDateRange(
            @Param("tenantId") UUID tenantId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    // ── Average order value ───────────────────────────────────────────────────
    @Query("SELECT COALESCE(AVG(s.totalAmount), 0) FROM Sale s " +
           "WHERE s.tenantId = :tenantId " +
           "AND s.createdAt BETWEEN :from AND :to " +
           "AND s.status != 'REFUNDED'")
    BigDecimal avgOrderValue(
            @Param("tenantId") UUID tenantId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    // ── Daily revenue (last 30 days) ──────────────────────────────────────────
    // Returns: date, gross, net, count, refunded
    @Query("SELECT CAST(s.createdAt AS date), " +
           "COALESCE(SUM(s.totalAmount), 0), " +
           "COALESCE(SUM(s.totalAmount - COALESCE(s.totalRefundedAmount,0)), 0), " +
           "COUNT(s), " +
           "COALESCE(SUM(COALESCE(s.totalRefundedAmount,0)), 0) " +
           "FROM Sale s " +
           "WHERE s.tenantId = :tenantId " +
           "AND s.createdAt BETWEEN :from AND :to " +
           "AND s.status != 'REFUNDED' " +
           "GROUP BY CAST(s.createdAt AS date) " +
           "ORDER BY CAST(s.createdAt AS date) ASC")
    List<Object[]> findDailyRevenue(
            @Param("tenantId") UUID tenantId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    // ── Monthly revenue (last 12 months) ──────────────────────────────────────
    // Returns: year, month, gross, net, count, refunded
    @Query(value =
           "SELECT EXTRACT(YEAR FROM created_at) AS yr, " +
           "EXTRACT(MONTH FROM created_at) AS mo, " +
           "COALESCE(SUM(total_amount), 0) AS gross, " +
           "COALESCE(SUM(total_amount - COALESCE(total_refunded_amount,0)), 0) AS net, " +
           "COUNT(*) AS cnt, " +
           "COALESCE(SUM(COALESCE(total_refunded_amount,0)), 0) AS refunded " +
           "FROM sales " +
           "WHERE tenant_id = :tenantId " +
           "AND created_at >= :from " +
           "AND status != 'REFUNDED' " +
           "GROUP BY yr, mo " +
           "ORDER BY yr ASC, mo ASC",
           nativeQuery = true)
    List<Object[]> findMonthlyRevenue(
            @Param("tenantId") UUID tenantId,
            @Param("from") LocalDateTime from);

    // ── Payment method breakdown ──────────────────────────────────────────────
    @Query("SELECT s.paymentMethod, COUNT(s), COALESCE(SUM(s.totalAmount), 0) " +
           "FROM Sale s " +
           "WHERE s.tenantId = :tenantId " +
           "AND s.createdAt BETWEEN :from AND :to " +
           "AND s.status != 'REFUNDED' " +
           "GROUP BY s.paymentMethod")
    List<Object[]> findPaymentBreakdown(
            @Param("tenantId") UUID tenantId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    // ── Peak hours ────────────────────────────────────────────────────────────
    @Query(value =
           "SELECT EXTRACT(HOUR FROM created_at) AS hr, " +
           "COUNT(*) AS cnt, " +
           "COALESCE(SUM(total_amount), 0) AS rev " +
           "FROM sales " +
           "WHERE tenant_id = :tenantId " +
           "AND created_at BETWEEN :from AND :to " +
           "AND status != 'REFUNDED' " +
           "GROUP BY hr ORDER BY hr ASC",
           nativeQuery = true)
    List<Object[]> findPeakHours(
            @Param("tenantId") UUID tenantId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    // ── Daily revenue for single products (findDailySales alias) ─────────────
    @Query("SELECT CAST(s.createdAt AS date), " +
           "COALESCE(SUM(s.totalAmount - COALESCE(s.totalRefundedAmount,0)), 0), " +
           "COUNT(s), " +
           "COALESCE(SUM(COALESCE(s.totalRefundedAmount,0)), 0) " +
           "FROM Sale s " +
           "WHERE s.tenantId = :tenantId " +
           "AND s.createdAt BETWEEN :from AND :to " +
           "AND s.status != 'REFUNDED' " +
           "GROUP BY CAST(s.createdAt AS date) " +
           "ORDER BY CAST(s.createdAt AS date) ASC")
    List<Object[]> findDailySales(
            @Param("tenantId") UUID tenantId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}
