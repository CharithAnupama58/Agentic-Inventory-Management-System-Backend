package com.pos.system.service;

import com.pos.system.config.CacheConfig;
import com.pos.system.dto.DashboardDto;
import com.pos.system.model.Product;
import com.pos.system.repository.*;
import com.pos.system.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final SaleRepository     saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final ProductRepository  productRepository;

    private static final int LOW_STOCK_THRESHOLD = 10;

    // ── Get Summary (cached) ──────────────────────────────────────────────────
    @Cacheable(
        value = CacheConfig.CACHE_DASHBOARD,
        key = "'summary:' + T(com.pos.system.security.TenantContext).getTenantId()"
    )
    public DashboardDto.SummaryResponse getSummary() {
        UUID tenantId = TenantContext.getTenantId();

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd   = todayStart.plusDays(1).minusNanos(1);
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime monthEnd   = todayEnd;
        LocalDateTime weekStart  = LocalDate.now().minusDays(6).atStartOfDay();

        return DashboardDto.SummaryResponse.builder()
                .today(buildSalesOverview(tenantId, todayStart, todayEnd))
                .thisMonth(buildSalesOverview(tenantId, monthStart, monthEnd))
                .topProducts(buildTopProducts(tenantId, monthStart, monthEnd))
                .dailySales(buildDailySales(tenantId, weekStart, todayEnd))
                .inventory(buildInventoryOverview(tenantId))
                .refundSummary(buildRefundSummary(tenantId, monthStart, monthEnd))
                .build();
    }

    // ── Evict dashboard cache ─────────────────────────────────────────────────
    // Called by SaleService after every sale or refund
    @CacheEvict(value = CacheConfig.CACHE_DASHBOARD, allEntries = true)
    public void evictDashboardCache() {
        // Spring AOP handles the eviction — no body needed
    }

    // ── Sales Overview ────────────────────────────────────────────────────────
    private DashboardDto.SalesOverview buildSalesOverview(
            UUID tenantId, LocalDateTime from, LocalDateTime to) {

        BigDecimal grossRevenue = saleRepository
                .sumRevenueByTenantAndDateRange(tenantId, from, to);

        BigDecimal refundedAmount = saleRepository
                .sumRefundedByTenantAndDateRange(tenantId, from, to);

        BigDecimal netRevenue = grossRevenue
                .subtract(refundedAmount)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal cogs = saleItemRepository
                .sumCostOfGoodsSold(tenantId, from, to);

        BigDecimal profit = netRevenue
                .subtract(cogs)
                .setScale(2, RoundingMode.HALF_UP);

        Long transactionCount = saleRepository
                .countByTenantAndDateRange(tenantId, from, to);

        Long refundCount = saleRepository
                .countRefundsByTenantAndDateRange(tenantId, from, to);

        return DashboardDto.SalesOverview.builder()
                .revenue(grossRevenue.setScale(2, RoundingMode.HALF_UP))
                .refundedAmount(refundedAmount.setScale(2, RoundingMode.HALF_UP))
                .netRevenue(netRevenue)
                .cogs(cogs.setScale(2, RoundingMode.HALF_UP))
                .profit(profit)
                .transactionCount(transactionCount)
                .refundCount(refundCount)
                .build();
    }

    // ── Top Products ──────────────────────────────────────────────────────────
    private List<DashboardDto.TopProduct> buildTopProducts(
            UUID tenantId, LocalDateTime from, LocalDateTime to) {

        List<Object[]> rows = saleItemRepository
                .findTopProductsByQuantity(tenantId, from, to);

        List<DashboardDto.TopProduct> result = new ArrayList<>();
        int limit = Math.min(rows.size(), 5);

        for (int i = 0; i < limit; i++) {
            Object[]   row       = rows.get(i);
            UUID       productId = (UUID) row[0];
            Long       qty       = ((Number) row[1]).longValue();
            BigDecimal rev       = (BigDecimal) row[2];

            String name = productRepository.findById(productId)
                    .map(Product::getName).orElse("Deleted Product");

            result.add(DashboardDto.TopProduct.builder()
                    .productId(productId.toString())
                    .productName(name)
                    .quantitySold(qty)
                    .totalRevenue(rev.setScale(2, RoundingMode.HALF_UP))
                    .build());
        }
        return result;
    }

    // ── Daily Sales ───────────────────────────────────────────────────────────
    private List<DashboardDto.DailySale> buildDailySales(
            UUID tenantId, LocalDateTime from, LocalDateTime to) {

        return saleRepository.findDailySales(tenantId, from, to)
                .stream().map(row -> {
                    BigDecimal net      = new BigDecimal(row[1].toString())
                            .setScale(2, RoundingMode.HALF_UP);
                    BigDecimal refunded = new BigDecimal(row[3].toString())
                            .setScale(2, RoundingMode.HALF_UP);
                    BigDecimal gross    = net.add(refunded)
                            .setScale(2, RoundingMode.HALF_UP);

                    return DashboardDto.DailySale.builder()
                            .date(row[0].toString())
                            .grossRevenue(gross)
                            .refundedAmount(refunded)
                            .netRevenue(net)
                            .transactionCount(((Number) row[2]).longValue())
                            .build();
                }).collect(Collectors.toList());
    }

    // ── Inventory Overview ────────────────────────────────────────────────────
    private DashboardDto.InventoryOverview buildInventoryOverview(UUID tenantId) {
        List<Product> products = productRepository.findAllByTenantId(tenantId);

        return DashboardDto.InventoryOverview.builder()
                .totalProducts((long) products.size())
                .lowStockCount(products.stream()
                        .filter(p -> p.getStock() > 0
                                && p.getStock() <= LOW_STOCK_THRESHOLD)
                        .count())
                .outOfStockCount(products.stream()
                        .filter(p -> p.getStock() == 0)
                        .count())
                .build();
    }

    // ── Refund Summary ────────────────────────────────────────────────────────
    private DashboardDto.RefundSummary buildRefundSummary(
            UUID tenantId, LocalDateTime from, LocalDateTime to) {

        return DashboardDto.RefundSummary.builder()
                .totalRefunds(saleRepository
                        .countRefundsByTenantAndDateRange(tenantId, from, to))
                .totalRefundedAmount(saleRepository
                        .sumRefundedByTenantAndDateRange(tenantId, from, to)
                        .setScale(2, RoundingMode.HALF_UP))
                .build();
    }
}
