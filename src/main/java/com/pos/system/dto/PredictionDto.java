package com.pos.system.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class PredictionDto {

    // ── Single product prediction ─────────────────────────────────────────────
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ProductPrediction {
        private String     productId;
        private String     productName;
        private Integer    currentStock;

        // Sales velocity
        private BigDecimal avgDailySales;        // last 7 days
        private BigDecimal avgDailySales30d;     // last 30 days
        private BigDecimal avgDailySalesTrend;   // trending up/down %

        // Predictions
        private Integer    daysUntilStockout;    // when will it run out
        private LocalDate  predictedStockoutDate;
        private Integer    recommendedReorderQty; // how much to order
        private Integer    reorderPoint;          // trigger level

        // Risk
        private String     riskLevel;            // CRITICAL HIGH MEDIUM LOW SAFE
        private String     riskIcon;
        private String     prediction;           // human readable message
        private String     action;               // recommended action

        // Forecast (next 7 days stock levels)
        private List<DayForecast> forecast;

        // AI message
        private String     aiMessage;
    }

    // ── One day in forecast ───────────────────────────────────────────────────
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DayForecast {
        private String  date;
        private Integer predictedStock;
        private BigDecimal predictedSales;
        private boolean isStockout;
    }

    // ── Full prediction response ──────────────────────────────────────────────
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PredictionResponse {
        private List<ProductPrediction> criticalProducts;  // stockout < 3 days
        private List<ProductPrediction> highRiskProducts;  // stockout 3-7 days
        private List<ProductPrediction> mediumRiskProducts;// stockout 7-14 days
        private List<ProductPrediction> safeProducts;      // stockout > 14 days
        private List<ProductPrediction> allPredictions;    // full list
        private PredictionSummary       summary;
        private String                  aiAnalysis;        // Groq AI analysis
        private String                  generatedAt;
    }

    // ── Summary stats ─────────────────────────────────────────────────────────
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PredictionSummary {
        private int        totalProductsAnalyzed;
        private int        criticalCount;
        private int        highRiskCount;
        private int        mediumRiskCount;
        private int        safeCount;
        private BigDecimal totalReorderCost;      // estimated cost to restock
        private String     mostUrgentProduct;
        private Integer    mostUrgentDaysLeft;
    }
}
