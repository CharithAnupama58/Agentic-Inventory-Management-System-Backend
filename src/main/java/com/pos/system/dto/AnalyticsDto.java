package com.pos.system.dto;

import lombok.*;
import java.math.BigDecimal;
import java.util.List;

public class AnalyticsDto {

    // ── Full analytics response ───────────────────────────────────────────────
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class AnalyticsResponse {
        private RevenueSummary revenue;
        private List<PeriodData> dailyRevenue;      // last 30 days
        private List<PeriodData> monthlyRevenue;    // last 12 months
        private List<ProductPerformance> topProducts;
        private List<ProductPerformance> slowMoving;
        private List<CategoryBreakdown> paymentBreakdown;
        private ProfitAnalysis profit;
        private List<HourlyData> peakHours;         // busiest hours
    }

    // ── Revenue summary ───────────────────────────────────────────────────────
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RevenueSummary {
        private BigDecimal todayRevenue;
        private BigDecimal todayProfit;
        private BigDecimal weekRevenue;
        private BigDecimal weekProfit;
        private BigDecimal monthRevenue;
        private BigDecimal monthProfit;
        private BigDecimal monthRefunds;
        private Long todayTransactions;
        private Long weekTransactions;
        private Long monthTransactions;
        private BigDecimal avgOrderValue;           // month avg
        private BigDecimal revenueGrowth;           // vs last month %
        private BigDecimal profitMargin;            // month profit %
    }

    // ── One data point for charts ─────────────────────────────────────────────
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PeriodData {
        private String label;                       // "2026-03-27" or "Mar 2026"
        private BigDecimal revenue;
        private BigDecimal netRevenue;
        private BigDecimal profit;
        private Long transactions;
        private BigDecimal refunds;
    }

    // ── Product performance ───────────────────────────────────────────────────
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ProductPerformance {
        private String productId;
        private String productName;
        private Long quantitySold;
        private BigDecimal revenue;
        private BigDecimal profit;
        private BigDecimal profitMargin;            // %
        private Integer currentStock;
        private BigDecimal avgDailySales;
        private String trend;                       // UP, DOWN, STABLE
    }

    // ── Payment method breakdown ──────────────────────────────────────────────
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CategoryBreakdown {
        private String label;
        private Long count;
        private BigDecimal amount;
        private BigDecimal percentage;
    }

    // ── Profit analysis ───────────────────────────────────────────────────────
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ProfitAnalysis {
        private BigDecimal grossProfit;
        private BigDecimal netProfit;
        private BigDecimal cogs;
        private BigDecimal refunds;
        private BigDecimal profitMarginPercent;
        private List<ProductPerformance> highestMarginProducts;
        private List<ProductPerformance> lowestMarginProducts;
    }

    // ── Hourly sales pattern ──────────────────────────────────────────────────
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class HourlyData {
        private Integer hour;                       // 0-23
        private String label;                       // "9 AM"
        private Long transactions;
        private BigDecimal revenue;
    }
}
