package com.pos.system.service;

import com.pos.system.dto.PredictionDto;
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
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PredictionEngineService {

    private final ProductRepository      productRepository;
    private final InventoryLogRepository logRepository;
    private final SaleItemRepository     saleItemRepository;

    private static final int LEAD_TIME_DAYS    = 3;  // days to receive order
    private static final int SAFETY_STOCK_DAYS = 2;  // buffer stock days
    private static final int FORECAST_DAYS     = 7;  // forecast window

    // ── Build all predictions ─────────────────────────────────────────────────
    public List<PredictionDto.ProductPrediction> buildAllPredictions() {
        UUID tenantId = TenantContext.getTenantId();
        List<Product> products = productRepository.findAllByTenantId(tenantId);

        log.info("Building predictions for {} products", products.size());

        return products.stream()
                .map(p -> buildProductPrediction(p, tenantId))
                .sorted(Comparator.comparingInt(
                        pred -> pred.getDaysUntilStockout() != null
                                ? pred.getDaysUntilStockout() : 999))
                .collect(Collectors.toList());
    }

    // ── Build prediction for one product ─────────────────────────────────────
    public PredictionDto.ProductPrediction buildProductPrediction(
            Product product, UUID tenantId) {

        LocalDateTime now      = LocalDateTime.now();
        LocalDateTime since7d  = now.minusDays(7);
        LocalDateTime since30d = now.minusDays(30);
        LocalDateTime since14d = now.minusDays(14);

        // ── Sales velocity calculations ───────────────────────────────────────
        BigDecimal salesLast7d  = getSalesVelocity(
                tenantId, product.getId(), since7d,  now, 7);
        BigDecimal salesLast30d = getSalesVelocity(
                tenantId, product.getId(), since30d, now, 30);
        BigDecimal salesLast14d = getSalesVelocity(
                tenantId, product.getId(), since14d, now, 14);

        // ── Trend: compare last 7d vs previous 7d ────────────────────────────
        LocalDateTime prev7dStart = now.minusDays(14);
        LocalDateTime prev7dEnd   = now.minusDays(7);
        BigDecimal prevWeekSales  = getSalesVelocity(
                tenantId, product.getId(), prev7dStart, prev7dEnd, 7);

        BigDecimal trendPct = BigDecimal.ZERO;
        if (prevWeekSales.compareTo(BigDecimal.ZERO) > 0) {
            trendPct = salesLast7d.subtract(prevWeekSales)
                    .divide(prevWeekSales, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(1, RoundingMode.HALF_UP);
        }

        // ── Use weighted avg: 60% last 7d + 40% last 30d ─────────────────────
        BigDecimal weightedAvg = salesLast7d
                .multiply(BigDecimal.valueOf(0.6))
                .add(salesLast30d.multiply(BigDecimal.valueOf(0.4)))
                .setScale(2, RoundingMode.HALF_UP);

        // Apply trend adjustment
        if (trendPct.compareTo(BigDecimal.ZERO) != 0) {
            BigDecimal trendFactor = BigDecimal.ONE
                    .add(trendPct.divide(BigDecimal.valueOf(100),
                            4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(0.3))); // 30% weight to trend
            weightedAvg = weightedAvg.multiply(trendFactor)
                    .setScale(2, RoundingMode.HALF_UP);
        }

        // Minimum 0.01 to avoid division by zero
        if (weightedAvg.compareTo(BigDecimal.ZERO) <= 0)
            weightedAvg = BigDecimal.valueOf(0.01);

        // ── Days until stockout ───────────────────────────────────────────────
        int daysLeft = (int) Math.floor(
                product.getStock() / weightedAvg.doubleValue());

        LocalDate stockoutDate = LocalDate.now().plusDays(daysLeft);

        // ── Reorder calculations ──────────────────────────────────────────────
        // Reorder point = (avg daily sales × lead time) + safety stock
        int reorderPoint = (int) Math.ceil(
                weightedAvg.doubleValue()
                * (LEAD_TIME_DAYS + SAFETY_STOCK_DAYS));

        // Suggested order = 30 days of stock
        int suggestedQty = (int) Math.ceil(weightedAvg.doubleValue() * 30);
        suggestedQty = Math.max(suggestedQty, 10); // minimum 10

        // ── Risk level ────────────────────────────────────────────────────────
        String riskLevel;
        String riskIcon;
        String prediction;
        String action;

        if (product.getStock() == 0) {
            riskLevel  = "CRITICAL";
            riskIcon   = "🚨";
            prediction = "OUT OF STOCK — losing sales right now!";
            action     = "Order " + suggestedQty + " units immediately";
        } else if (daysLeft <= LEAD_TIME_DAYS) {
            riskLevel  = "CRITICAL";
            riskIcon   = "🚨";
            prediction = "Will run out in " + daysLeft
                    + " day" + (daysLeft == 1 ? "" : "s")
                    + " — before your restock arrives!";
            action     = "Emergency order needed: " + suggestedQty + " units";
        } else if (daysLeft <= 7) {
            riskLevel  = "HIGH";
            riskIcon   = "⚠️";
            prediction = "Will run out in " + daysLeft + " days";
            action     = "Reorder soon: " + suggestedQty + " units";
        } else if (daysLeft <= 14) {
            riskLevel  = "MEDIUM";
            riskIcon   = "📦";
            prediction = "Will run out in " + daysLeft + " days";
            action     = "Plan reorder: " + suggestedQty + " units";
        } else if (daysLeft <= 30) {
            riskLevel  = "LOW";
            riskIcon   = "✅";
            prediction = "Stock lasts ~" + daysLeft + " days";
            action     = "Monitor — reorder when below " + reorderPoint;
        } else {
            riskLevel  = "SAFE";
            riskIcon   = "✅";
            prediction = "Well stocked for " + daysLeft + "+ days";
            action     = "No action needed";
        }

        // ── 7-day forecast ────────────────────────────────────────────────────
        List<PredictionDto.DayForecast> forecast = new ArrayList<>();
        int runningStock = product.getStock();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM dd");

        for (int day = 1; day <= FORECAST_DAYS; day++) {
            int dailySales = (int) Math.ceil(weightedAvg.doubleValue());
            runningStock = Math.max(0, runningStock - dailySales);
            LocalDate forecastDate = LocalDate.now().plusDays(day);

            forecast.add(PredictionDto.DayForecast.builder()
                    .date(forecastDate.format(fmt))
                    .predictedStock(runningStock)
                    .predictedSales(BigDecimal.valueOf(dailySales))
                    .isStockout(runningStock == 0)
                    .build());
        }

        return PredictionDto.ProductPrediction.builder()
                .productId(product.getId().toString())
                .productName(product.getName())
                .currentStock(product.getStock())
                .avgDailySales(salesLast7d)
                .avgDailySales30d(salesLast30d)
                .avgDailySalesTrend(trendPct)
                .daysUntilStockout(daysLeft)
                .predictedStockoutDate(stockoutDate)
                .recommendedReorderQty(suggestedQty)
                .reorderPoint(reorderPoint)
                .riskLevel(riskLevel)
                .riskIcon(riskIcon)
                .prediction(prediction)
                .action(action)
                .forecast(forecast)
                .build();
    }

    // ── Get avg daily sales for product in date range ─────────────────────────
    private BigDecimal getSalesVelocity(UUID tenantId, UUID productId,
                                         LocalDateTime from,
                                         LocalDateTime to, int days) {
        List<Object[]> rows = saleItemRepository
                .findProductPerformance(tenantId, from, to);

        for (Object[] row : rows) {
            UUID pid = (UUID) row[0];
            if (pid.equals(productId)) {
                long qty = ((Number) row[1]).longValue();
                return BigDecimal.valueOf(qty)
                        .divide(BigDecimal.valueOf(days),
                                2, RoundingMode.HALF_UP);
            }
        }
        return BigDecimal.ZERO;
    }

    // ── Build summary stats ───────────────────────────────────────────────────
    public PredictionDto.PredictionSummary buildSummary(
            List<PredictionDto.ProductPrediction> predictions,
            List<Product> products) {

        int critical = 0, high = 0, medium = 0, safe = 0;
        BigDecimal totalReorderCost = BigDecimal.ZERO;
        String mostUrgent = null;
        int mostUrgentDays = Integer.MAX_VALUE;

        for (PredictionDto.ProductPrediction p : predictions) {
            switch (p.getRiskLevel()) {
                case "CRITICAL" -> critical++;
                case "HIGH"     -> high++;
                case "MEDIUM"   -> medium++;
                default         -> safe++;
            }

            // Estimate reorder cost
            Product product = products.stream()
                    .filter(pr -> pr.getId().toString()
                            .equals(p.getProductId()))
                    .findFirst().orElse(null);
            if (product != null && p.getRecommendedReorderQty() != null) {
                totalReorderCost = totalReorderCost.add(
                        product.getCostPrice().multiply(
                                BigDecimal.valueOf(
                                        p.getRecommendedReorderQty())));
            }

            // Track most urgent
            if (p.getDaysUntilStockout() != null
                    && p.getDaysUntilStockout() < mostUrgentDays
                    && !p.getRiskLevel().equals("SAFE")) {
                mostUrgentDays = p.getDaysUntilStockout();
                mostUrgent     = p.getProductName();
            }
        }

        return PredictionDto.PredictionSummary.builder()
                .totalProductsAnalyzed(predictions.size())
                .criticalCount(critical)
                .highRiskCount(high)
                .mediumRiskCount(medium)
                .safeCount(safe)
                .totalReorderCost(totalReorderCost
                        .setScale(2, RoundingMode.HALF_UP))
                .mostUrgentProduct(mostUrgent)
                .mostUrgentDaysLeft(
                        mostUrgentDays == Integer.MAX_VALUE
                                ? null : mostUrgentDays)
                .build();
    }
}
