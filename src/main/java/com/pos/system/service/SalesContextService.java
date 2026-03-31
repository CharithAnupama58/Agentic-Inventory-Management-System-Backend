package com.pos.system.service;

import com.pos.system.dto.InsightsDto;
import com.pos.system.model.Product;
import com.pos.system.repository.*;
import com.pos.system.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SalesContextService {

    private final SaleRepository     saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final ProductRepository  productRepository;
    private final ReorderSuggestionRepository reorderRepository;

    private static final int LOW_STOCK = 10;

    public InsightsDto.SalesContext buildContext() {
        UUID tenantId = TenantContext.getTenantId();
        LocalDateTime now            = LocalDateTime.now();

        LocalDateTime todayStart     = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd       = todayStart.plusDays(1).minusNanos(1);
        LocalDateTime yesterdayStart = todayStart.minusDays(1);
        LocalDateTime yesterdayEnd   = todayStart.minusNanos(1);
        LocalDateTime weekStart      = now.minusDays(7);
        LocalDateTime monthStart     = LocalDate.now()
                                        .withDayOfMonth(1).atStartOfDay();
        LocalDateTime lastMonthStart = monthStart.minusMonths(1);
        LocalDateTime lastMonthEnd   = monthStart.minusNanos(1);

        // ── Today ─────────────────────────────────────────────────────────────
        BigDecimal todayRev  = netRevenue(tenantId, todayStart, todayEnd);
        BigDecimal todayCogs = saleItemRepository
                .sumCostOfGoodsSold(tenantId, todayStart, todayEnd);
        BigDecimal todayProfit   = todayRev.subtract(todayCogs)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal todayRefunds  = saleRepository
                .sumRefundedByTenantAndDateRange(tenantId, todayStart, todayEnd);
        Long todayTxn = saleRepository
                .countByTenantAndDateRange(tenantId, todayStart, todayEnd);

        // ── Yesterday ─────────────────────────────────────────────────────────
        BigDecimal yestRev   = netRevenue(tenantId, yesterdayStart, yesterdayEnd);
        BigDecimal yestCogs  = saleItemRepository
                .sumCostOfGoodsSold(tenantId, yesterdayStart, yesterdayEnd);
        BigDecimal yestProfit = yestRev.subtract(yestCogs)
                .setScale(2, RoundingMode.HALF_UP);
        Long yestTxn = saleRepository
                .countByTenantAndDateRange(tenantId, yesterdayStart, yesterdayEnd);

        // ── Week ──────────────────────────────────────────────────────────────
        BigDecimal weekRev   = netRevenue(tenantId, weekStart, now);
        BigDecimal weekCogs  = saleItemRepository
                .sumCostOfGoodsSold(tenantId, weekStart, now);
        BigDecimal weekProfit = weekRev.subtract(weekCogs)
                .setScale(2, RoundingMode.HALF_UP);
        Long weekTxn = saleRepository
                .countByTenantAndDateRange(tenantId, weekStart, now);

        // ── Month ─────────────────────────────────────────────────────────────
        BigDecimal monthRev   = netRevenue(tenantId, monthStart, now);
        BigDecimal monthCogs  = saleItemRepository
                .sumCostOfGoodsSold(tenantId, monthStart, now);
        BigDecimal monthProfit = monthRev.subtract(monthCogs)
                .setScale(2, RoundingMode.HALF_UP);
        Long monthTxn = saleRepository
                .countByTenantAndDateRange(tenantId, monthStart, now);

        // ── Last month ────────────────────────────────────────────────────────
        BigDecimal lastMonthRev = netRevenue(tenantId,
                lastMonthStart, lastMonthEnd);

        // ── Growth % ──────────────────────────────────────────────────────────
        BigDecimal growthVsYesterday = growth(todayRev, yestRev);
        BigDecimal growthVsLastMonth = growth(monthRev, lastMonthRev);

        // ── Profit margin % ───────────────────────────────────────────────────
        BigDecimal profitMargin = monthRev.compareTo(BigDecimal.ZERO) > 0
                ? monthProfit.divide(monthRev, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // ── Top products today ────────────────────────────────────────────────
        List<InsightsDto.ProductStat> topToday =
                buildProductStats(tenantId, todayStart, todayEnd, 5);

        // ── Top products this week ────────────────────────────────────────────
        List<InsightsDto.ProductStat> topWeek =
                buildProductStats(tenantId, weekStart, now, 5);

        // ── Inventory stats ───────────────────────────────────────────────────
        List<Product> products = productRepository.findAllByTenantId(tenantId);
        long outOfStock = products.stream()
                .filter(p -> p.getStock() == 0).count();
        long lowStock = products.stream()
                .filter(p -> p.getStock() > 0
                        && p.getStock() <= LOW_STOCK).count();
        long criticalReorders = reorderRepository
                .findByTenantIdAndStatusOrderByUrgencyAsc(
                        tenantId,
                        com.pos.system.model.ReorderSuggestion
                                .SuggestionStatus.PENDING)
                .stream()
                .filter(s -> s.getUrgency() ==
                        com.pos.system.model.ReorderSuggestion.Urgency.CRITICAL)
                .count();

        // ── Payment breakdown today ───────────────────────────────────────────
        BigDecimal cashRev  = BigDecimal.ZERO;
        BigDecimal cardRev  = BigDecimal.ZERO;
        BigDecimal splitRev = BigDecimal.ZERO;

        for (Object[] row : saleRepository
                .findPaymentBreakdown(tenantId, todayStart, todayEnd)) {
            String method = row[0] != null ? row[0].toString() : "";
            BigDecimal amt = new BigDecimal(row[2].toString());
            switch (method) {
                case "CASH"  -> cashRev  = amt;
                case "CARD"  -> cardRev  = amt;
                case "SPLIT" -> splitRev = amt;
            }
        }

        // ── Peak hour today ───────────────────────────────────────────────────
        String peakHour = "N/A";
        List<Object[]> hours = saleRepository
                .findPeakHours(tenantId, todayStart, todayEnd);
        if (!hours.isEmpty()) {
            Object[] top = hours.stream()
                    .max(Comparator.comparingLong(
                            r -> ((Number) r[1]).longValue()))
                    .orElse(null);
            if (top != null) {
                int h = ((Number) top[0]).intValue();
                peakHour = h == 0 ? "12 AM"
                        : h < 12 ? h + " AM"
                        : h == 12 ? "12 PM"
                        : (h - 12) + " PM";
            }
        }

        // ── Slow movers ───────────────────────────────────────────────────────
        Set<UUID> activeSellers = new HashSet<>(
                saleItemRepository.findProductsWithSalesSince(
                        tenantId, now.minusDays(30)));
        long slowMoving = products.stream()
                .filter(p -> p.getStock() > 0
                        && !activeSellers.contains(p.getId()))
                .count();

        return InsightsDto.SalesContext.builder()
                .todayRevenue(todayRev)
                .todayProfit(todayProfit)
                .todayTransactions(todayTxn)
                .todayRefunds(todayRefunds)
                .yesterdayRevenue(yestRev)
                .yesterdayProfit(yestProfit)
                .yesterdayTransactions(yestTxn)
                .weekRevenue(weekRev)
                .weekProfit(weekProfit)
                .weekTransactions(weekTxn)
                .monthRevenue(monthRev)
                .monthProfit(monthProfit)
                .monthTransactions(monthTxn)
                .topProductsToday(topToday)
                .topProductsWeek(topWeek)
                .outOfStockCount(outOfStock)
                .lowStockCount(lowStock)
                .criticalReorders(criticalReorders)
                .cashRevenue(cashRev)
                .cardRevenue(cardRev)
                .splitRevenue(splitRev)
                .slowMovingCount(slowMoving)
                .peakHourToday(peakHour)
                .revenueGrowthVsYesterday(growthVsYesterday)
                .revenueGrowthVsLastMonth(growthVsLastMonth)
                .profitMargin(profitMargin)
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private BigDecimal netRevenue(UUID tenantId,
                                   LocalDateTime from, LocalDateTime to) {
        BigDecimal gross = saleRepository
                .sumRevenueByTenantAndDateRange(tenantId, from, to);
        BigDecimal refunds = saleRepository
                .sumRefundedByTenantAndDateRange(tenantId, from, to);
        return gross.subtract(refunds).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal growth(BigDecimal current, BigDecimal previous) {
        if (previous.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return current.subtract(previous)
                .divide(previous, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);
    }

    private List<InsightsDto.ProductStat> buildProductStats(
            UUID tenantId, LocalDateTime from, LocalDateTime to, int limit) {

        return saleItemRepository
                .findProductPerformance(tenantId, from, to)
                .stream().limit(limit)
                .map(row -> {
                    UUID pid = (UUID) row[0];
                    String name = productRepository.findById(pid)
                            .map(Product::getName).orElse("Unknown");
                    int stock = productRepository.findById(pid)
                            .map(Product::getStock).orElse(0);
                    return InsightsDto.ProductStat.builder()
                            .name(name)
                            .quantitySold(((Number) row[1]).longValue())
                            .revenue(new BigDecimal(row[2].toString())
                                    .setScale(2, RoundingMode.HALF_UP))
                            .currentStock(stock)
                            .build();
                })
                .collect(Collectors.toList());
    }
}
