package com.pos.system.service;

import com.pos.system.dto.AnalyticsDto;
import com.pos.system.model.Product;
import com.pos.system.repository.*;
import com.pos.system.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final SaleRepository     saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final ProductRepository  productRepository;

    private static final DateTimeFormatter MONTH_FMT =
            DateTimeFormatter.ofPattern("MMM yyyy");

    // ── Full Analytics ────────────────────────────────────────────────────────
    public AnalyticsDto.AnalyticsResponse getFullAnalytics() {
        UUID tenantId = TenantContext.getTenantId();

        LocalDateTime now        = LocalDateTime.now();
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd   = todayStart.plusDays(1).minusNanos(1);
        LocalDateTime weekStart  = now.minusDays(7);
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime last30     = now.minusDays(30);
        LocalDateTime last12mo   = now.minusMonths(12);

        return AnalyticsDto.AnalyticsResponse.builder()
                .revenue(buildRevenueSummary(tenantId,
                        todayStart, todayEnd, weekStart, monthStart, now))
                .dailyRevenue(buildDailyRevenue(tenantId, last30, now))
                .monthlyRevenue(buildMonthlyRevenue(tenantId, last12mo))
                .topProducts(buildProductPerformance(tenantId, monthStart, now, 10))
                .slowMoving(buildSlowMoving(tenantId, now))
                .paymentBreakdown(buildPaymentBreakdown(tenantId, monthStart, now))
                .profit(buildProfitAnalysis(tenantId, monthStart, now))
                .peakHours(buildPeakHours(tenantId, last30, now))
                .build();
    }

    // ── Revenue Summary ───────────────────────────────────────────────────────
    private AnalyticsDto.RevenueSummary buildRevenueSummary(
            UUID tenantId,
            LocalDateTime todayStart, LocalDateTime todayEnd,
            LocalDateTime weekStart,  LocalDateTime monthStart,
            LocalDateTime now) {

        BigDecimal todayRev  = netRevenue(tenantId, todayStart, todayEnd);
        BigDecimal todayCogs = saleItemRepository.sumCostOfGoodsSold(tenantId, todayStart, todayEnd);
        Long todayTxn        = saleRepository.countByTenantAndDateRange(tenantId, todayStart, todayEnd);

        BigDecimal weekRev  = netRevenue(tenantId, weekStart, now);
        BigDecimal weekCogs = saleItemRepository.sumCostOfGoodsSold(tenantId, weekStart, now);
        Long weekTxn        = saleRepository.countByTenantAndDateRange(tenantId, weekStart, now);

        BigDecimal monthRev     = netRevenue(tenantId, monthStart, now);
        BigDecimal monthCogs    = saleItemRepository.sumCostOfGoodsSold(tenantId, monthStart, now);
        BigDecimal monthRefunds = saleRepository.sumRefundedByTenantAndDateRange(tenantId, monthStart, now);
        Long monthTxn           = saleRepository.countByTenantAndDateRange(tenantId, monthStart, now);
        BigDecimal avgOrderVal  = saleRepository.avgOrderValue(tenantId, monthStart, now);

        // Growth vs last month
        LocalDateTime lastMonthStart = monthStart.minusMonths(1);
        LocalDateTime lastMonthEnd   = monthStart.minusNanos(1);
        BigDecimal lastMonthRev = netRevenue(tenantId, lastMonthStart, lastMonthEnd);
        BigDecimal growth = lastMonthRev.compareTo(BigDecimal.ZERO) > 0
                ? monthRev.subtract(lastMonthRev)
                        .divide(lastMonthRev, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal monthProfit = monthRev.subtract(monthCogs);
        BigDecimal margin = monthRev.compareTo(BigDecimal.ZERO) > 0
                ? monthProfit.divide(monthRev, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return AnalyticsDto.RevenueSummary.builder()
                .todayRevenue(todayRev)
                .todayProfit(todayRev.subtract(todayCogs).setScale(2, RoundingMode.HALF_UP))
                .todayTransactions(todayTxn)
                .weekRevenue(weekRev)
                .weekProfit(weekRev.subtract(weekCogs).setScale(2, RoundingMode.HALF_UP))
                .weekTransactions(weekTxn)
                .monthRevenue(monthRev)
                .monthProfit(monthProfit.setScale(2, RoundingMode.HALF_UP))
                .monthRefunds(monthRefunds.setScale(2, RoundingMode.HALF_UP))
                .monthTransactions(monthTxn)
                .avgOrderValue(avgOrderVal.setScale(2, RoundingMode.HALF_UP))
                .revenueGrowth(growth)
                .profitMargin(margin)
                .build();
    }

    // ── Daily Revenue ─────────────────────────────────────────────────────────
    private List<AnalyticsDto.PeriodData> buildDailyRevenue(
            UUID tenantId, LocalDateTime from, LocalDateTime to) {

        return saleRepository.findDailyRevenue(tenantId, from, to)
                .stream().map(row -> AnalyticsDto.PeriodData.builder()
                        .label(row[0].toString())
                        .revenue(toBD(row[1]))
                        .netRevenue(toBD(row[2]))
                        .transactions(((Number) row[3]).longValue())
                        .refunds(toBD(row[4]))
                        .profit(BigDecimal.ZERO)
                        .build())
                .collect(Collectors.toList());
    }

    // ── Monthly Revenue ───────────────────────────────────────────────────────
    private List<AnalyticsDto.PeriodData> buildMonthlyRevenue(
            UUID tenantId, LocalDateTime from) {

        List<Object[]> rows = saleRepository.findMonthlyRevenue(tenantId, from);
        List<AnalyticsDto.PeriodData> result = new ArrayList<>();

        for (Object[] row : rows) {
            int year  = ((Number) row[0]).intValue();
            int month = ((Number) row[1]).intValue();
            String label = LocalDate.of(year, month, 1).format(MONTH_FMT);

            BigDecimal net      = toBD(row[3]);
            BigDecimal refunded = toBD(row[5]);

            LocalDateTime mStart = LocalDate.of(year, month, 1).atStartOfDay();
            LocalDateTime mEnd   = mStart.plusMonths(1).minusNanos(1);
            BigDecimal cogs = saleItemRepository.sumCostOfGoodsSold(tenantId, mStart, mEnd);

            result.add(AnalyticsDto.PeriodData.builder()
                    .label(label)
                    .revenue(toBD(row[2]))
                    .netRevenue(net)
                    .profit(net.subtract(cogs).setScale(2, RoundingMode.HALF_UP))
                    .transactions(((Number) row[4]).longValue())
                    .refunds(refunded)
                    .build());
        }
        return result;
    }

    // ── Product Performance ───────────────────────────────────────────────────
    private List<AnalyticsDto.ProductPerformance> buildProductPerformance(
            UUID tenantId, LocalDateTime from, LocalDateTime to, int limit) {

        List<Object[]> rows = saleItemRepository.findProductPerformance(tenantId, from, to);

        long periodDays = java.time.Duration.between(from, to).toDays();
        LocalDateTime prevFrom = from.minusDays(periodDays);

        // Previous period quantity map for trend calculation
        Map<UUID, Long> prevQty = new HashMap<>();
        for (Object[] row : saleItemRepository.findProductPerformance(tenantId, prevFrom, from)) {
            prevQty.put((UUID) row[0], ((Number) row[1]).longValue());
        }

        return rows.stream().limit(limit).map(row -> {
            UUID productId  = (UUID) row[0];
            long qty        = ((Number) row[1]).longValue();
            BigDecimal rev  = toBD(row[2]);
            BigDecimal cogs = toBD(row[3]);
            BigDecimal profit = rev.subtract(cogs);
            BigDecimal margin = rev.compareTo(BigDecimal.ZERO) > 0
                    ? profit.divide(rev, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(1, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            Product product = productRepository.findById(productId).orElse(null);
            int stock = product != null ? product.getStock() : 0;

            long prevCount = prevQty.getOrDefault(productId, 0L);
            String trend = qty > prevCount * 1.1 ? "UP"
                         : qty < prevCount * 0.9 ? "DOWN"
                         : "STABLE";

            BigDecimal avgDaily = BigDecimal.valueOf(qty)
                    .divide(BigDecimal.valueOf(Math.max(periodDays, 1)),
                            2, RoundingMode.HALF_UP);

            return AnalyticsDto.ProductPerformance.builder()
                    .productId(productId.toString())
                    .productName(product != null ? product.getName() : "Deleted")
                    .quantitySold(qty)
                    .revenue(rev.setScale(2, RoundingMode.HALF_UP))
                    .profit(profit.setScale(2, RoundingMode.HALF_UP))
                    .profitMargin(margin)
                    .currentStock(stock)
                    .avgDailySales(avgDaily)
                    .trend(trend)
                    .build();
        }).collect(Collectors.toList());
    }

    // ── Slow Moving ───────────────────────────────────────────────────────────
    private List<AnalyticsDto.ProductPerformance> buildSlowMoving(
            UUID tenantId, LocalDateTime now) {

        LocalDateTime since30 = now.minusDays(30);
        Set<UUID> activeSellers = new HashSet<>(
                saleItemRepository.findProductsWithSalesSince(tenantId, since30));

        return productRepository.findAllByTenantId(tenantId).stream()
                .filter(p -> p.getStock() > 0 && !activeSellers.contains(p.getId()))
                .map(p -> AnalyticsDto.ProductPerformance.builder()
                        .productId(p.getId().toString())
                        .productName(p.getName())
                        .quantitySold(0L)
                        .revenue(BigDecimal.ZERO)
                        .profit(BigDecimal.ZERO)
                        .profitMargin(BigDecimal.ZERO)
                        .currentStock(p.getStock())
                        .avgDailySales(BigDecimal.ZERO)
                        .trend("DOWN")
                        .build())
                .collect(Collectors.toList());
    }

    // ── Payment Breakdown ─────────────────────────────────────────────────────
    private List<AnalyticsDto.CategoryBreakdown> buildPaymentBreakdown(
            UUID tenantId, LocalDateTime from, LocalDateTime to) {

        List<Object[]> rows = saleRepository.findPaymentBreakdown(tenantId, from, to);

        BigDecimal total = rows.stream()
                .map(r -> toBD(r[2]))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return rows.stream().map(row -> {
            BigDecimal amount = toBD(row[2]);
            BigDecimal pct = total.compareTo(BigDecimal.ZERO) > 0
                    ? amount.divide(total, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(1, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            return AnalyticsDto.CategoryBreakdown.builder()
                    .label(row[0] != null ? row[0].toString() : "UNKNOWN")
                    .count(((Number) row[1]).longValue())
                    .amount(amount.setScale(2, RoundingMode.HALF_UP))
                    .percentage(pct)
                    .build();
        }).collect(Collectors.toList());
    }

    // ── Profit Analysis ───────────────────────────────────────────────────────
    private AnalyticsDto.ProfitAnalysis buildProfitAnalysis(
            UUID tenantId, LocalDateTime from, LocalDateTime to) {

        BigDecimal grossRev = saleRepository.sumRevenueByTenantAndDateRange(tenantId, from, to);
        BigDecimal netRev   = netRevenue(tenantId, from, to);
        BigDecimal cogs     = saleItemRepository.sumCostOfGoodsSold(tenantId, from, to);
        BigDecimal refunds  = saleRepository.sumRefundedByTenantAndDateRange(tenantId, from, to);

        BigDecimal grossProfit = grossRev.subtract(cogs);
        BigDecimal netProfit   = netRev.subtract(cogs);
        BigDecimal margin = netRev.compareTo(BigDecimal.ZERO) > 0
                ? netProfit.divide(netRev, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // ── Fix: use correctly typed list ─────────────────────────────────────
        List<Object[]> marginRows = saleItemRepository
                .findProductProfitMargins(tenantId, from, to);

        List<AnalyticsDto.ProductPerformance> highMargin = marginRows.stream()
                .limit(5)
                .map(this::marginRow)
                .collect(Collectors.toList());

        // Reverse a copy for lowest margin — correctly typed
        List<Object[]> reversed = new ArrayList<>(marginRows);
        Collections.reverse(reversed);
        List<AnalyticsDto.ProductPerformance> lowMargin = reversed.stream()
                .limit(5)
                .map(this::marginRow)
                .collect(Collectors.toList());

        return AnalyticsDto.ProfitAnalysis.builder()
                .grossProfit(grossProfit.setScale(2, RoundingMode.HALF_UP))
                .netProfit(netProfit.setScale(2, RoundingMode.HALF_UP))
                .cogs(cogs.setScale(2, RoundingMode.HALF_UP))
                .refunds(refunds.setScale(2, RoundingMode.HALF_UP))
                .profitMarginPercent(margin)
                .highestMarginProducts(highMargin)
                .lowestMarginProducts(lowMargin)
                .build();
    }

    // ── Peak Hours ────────────────────────────────────────────────────────────
    private List<AnalyticsDto.HourlyData> buildPeakHours(
            UUID tenantId, LocalDateTime from, LocalDateTime to) {

        Map<Integer, AnalyticsDto.HourlyData> hourMap = new LinkedHashMap<>();
        for (int h = 0; h < 24; h++) {
            String label = h == 0 ? "12 AM" : h < 12 ? h + " AM"
                         : h == 12 ? "12 PM" : (h - 12) + " PM";
            hourMap.put(h, AnalyticsDto.HourlyData.builder()
                    .hour(h).label(label)
                    .transactions(0L).revenue(BigDecimal.ZERO)
                    .build());
        }

        for (Object[] row : saleRepository.findPeakHours(tenantId, from, to)) {
            int hour = ((Number) row[0]).intValue();
            hourMap.get(hour).setTransactions(((Number) row[1]).longValue());
            hourMap.get(hour).setRevenue(toBD(row[2]));
        }

        return new ArrayList<>(hourMap.values());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private BigDecimal netRevenue(UUID tenantId,
                                   LocalDateTime from, LocalDateTime to) {
        BigDecimal gross   = saleRepository
                .sumRevenueByTenantAndDateRange(tenantId, from, to);
        BigDecimal refunds = saleRepository
                .sumRefundedByTenantAndDateRange(tenantId, from, to);
        return gross.subtract(refunds).setScale(2, RoundingMode.HALF_UP);
    }

    // ── Margin row mapper (takes Object[]) ────────────────────────────────────
    private AnalyticsDto.ProductPerformance marginRow(Object[] row) {
        UUID productId  = (UUID) row[0];
        BigDecimal rev  = toBD(row[1]);
        BigDecimal cogs = toBD(row[2]);
        BigDecimal profit  = rev.subtract(cogs);
        BigDecimal margin  = rev.compareTo(BigDecimal.ZERO) > 0
                ? profit.divide(rev, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        String name = productRepository.findById(productId)
                .map(Product::getName).orElse("Deleted");

        return AnalyticsDto.ProductPerformance.builder()
                .productId(productId.toString())
                .productName(name)
                .revenue(rev.setScale(2, RoundingMode.HALF_UP))
                .profit(profit.setScale(2, RoundingMode.HALF_UP))
                .profitMargin(margin)
                .quantitySold(0L)
                .currentStock(0)
                .avgDailySales(BigDecimal.ZERO)
                .trend("STABLE")
                .build();
    }

    private BigDecimal toBD(Object val) {
        if (val == null) return BigDecimal.ZERO;
        return new BigDecimal(val.toString()).setScale(2, RoundingMode.HALF_UP);
    }
}
