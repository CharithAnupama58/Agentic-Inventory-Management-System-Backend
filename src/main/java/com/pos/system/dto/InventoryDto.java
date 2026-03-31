package com.pos.system.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class InventoryDto {

    // ── Manual stock adjustment ───────────────────────────────────────────────
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class AdjustmentRequest {
        @NotNull private UUID productId;
        @NotNull private Integer quantity;        // positive=add, negative=remove
        @NotBlank private String movementType;    // RESTOCK, ADJUSTMENT, DAMAGED
        private String notes;
    }

    // ── Add a new batch ───────────────────────────────────────────────────────
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class BatchRequest {
        @NotNull  private UUID productId;
        @NotBlank private String batchNumber;
        @NotNull @Min(1) private Integer quantity;
        @NotNull  private BigDecimal costPrice;
        private LocalDate expiryDate;
        private LocalDate purchaseDate;
    }

    // ── Log response ──────────────────────────────────────────────────────────
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class LogResponse {
        private UUID id;
        private UUID productId;
        private String productName;
        private String movementType;
        private Integer quantity;
        private Integer stockBefore;
        private Integer stockAfter;
        private String notes;
        private LocalDateTime createdAt;
    }

    // ── Batch response ────────────────────────────────────────────────────────
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class BatchResponse {
        private UUID id;
        private UUID productId;
        private String productName;
        private String batchNumber;
        private Integer quantity;
        private Integer remainingQuantity;
        private BigDecimal costPrice;
        private LocalDate expiryDate;
        private LocalDate purchaseDate;
        private String status;
        private Long daysUntilExpiry;     // computed
        private boolean expiringSoon;     // within 30 days
    }

    // ── Reorder suggestion response ───────────────────────────────────────────
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ReorderResponse {
        private UUID id;
        private UUID productId;
        private String productName;
        private Integer currentStock;
        private Integer reorderPoint;
        private Integer suggestedQuantity;
        private BigDecimal avgDailySales;
        private Integer daysOfStockLeft;
        private String urgency;
        private String status;
        private LocalDateTime createdAt;
    }

    // ── Full inventory intelligence summary ───────────────────────────────────
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class IntelligenceSummary {
        private List<ReorderResponse> reorderSuggestions;
        private List<BatchResponse> expiringBatches;
        private List<LogResponse> recentMovements;
        private StockStats stats;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class StockStats {
        private Long totalProducts;
        private Long outOfStock;
        private Long lowStock;
        private Long criticalReorders;    // CRITICAL urgency
        private Long expiringIn30Days;
        private BigDecimal totalStockValue;   // SUM(stock × costPrice)
    }
}
