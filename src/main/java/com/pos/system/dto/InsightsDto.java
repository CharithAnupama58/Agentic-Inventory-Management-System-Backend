package com.pos.system.dto;

import lombok.*;
import java.math.BigDecimal;
import java.util.List;

public class InsightsDto {

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SalesContext {
        private BigDecimal todayRevenue;
        private BigDecimal todayProfit;
        private Long       todayTransactions;
        private BigDecimal todayRefunds;

        private BigDecimal yesterdayRevenue;
        private BigDecimal yesterdayProfit;
        private Long       yesterdayTransactions;

        private BigDecimal weekRevenue;
        private BigDecimal weekProfit;
        private Long       weekTransactions;

        private BigDecimal monthRevenue;
        private BigDecimal monthProfit;
        private Long       monthTransactions;

        private List<ProductStat> topProductsToday;
        private List<ProductStat> topProductsWeek;

        private Long outOfStockCount;
        private Long lowStockCount;
        private Long criticalReorders;
        private Long slowMovingCount;

        private BigDecimal cashRevenue;
        private BigDecimal cardRevenue;
        private BigDecimal splitRevenue;

        private String     peakHourToday;
        private BigDecimal revenueGrowthVsYesterday;
        private BigDecimal revenueGrowthVsLastMonth;
        private BigDecimal profitMargin;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ProductStat {
        private String     name;
        private Long       quantitySold;
        private BigDecimal revenue;
        private Integer    currentStock;
    }

    // ── provider field added ──────────────────────────────────────────────────
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class InsightResponse {
        private List<Insight> insights;
        private List<String>  alerts;
        private List<String>  recommendations;
        private String        summary;
        private SalesContext  rawData;
        private String        generatedAt;
        private String        provider;        // "Google Gemini 1.5 Flash" or "Rule-Based Engine"
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Insight {
        private String     type;
        private String     icon;
        private String     title;
        private String     detail;
        private BigDecimal changePercent;
    }
}
