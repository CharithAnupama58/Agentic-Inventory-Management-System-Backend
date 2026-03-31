package com.pos.system.dto;

import lombok.*;
import java.math.BigDecimal;
import java.util.List;

public class DashboardDto {

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SummaryResponse {
        private SalesOverview today;
        private SalesOverview thisMonth;
        private List<TopProduct> topProducts;
        private List<DailySale> dailySales;
        private InventoryOverview inventory;
        private RefundSummary refundSummary;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SalesOverview {
        private BigDecimal revenue;
        private BigDecimal refundedAmount;
        private BigDecimal netRevenue;
        private BigDecimal cogs;
        private BigDecimal profit;
        private Long transactionCount;
        private Long refundCount;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class TopProduct {
        private String productId;
        private String productName;
        private Long quantitySold;
        private BigDecimal totalRevenue;
    }

    // ── DailySale now includes refund breakdown ───────────────────────────────
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DailySale {
        private String date;
        private BigDecimal grossRevenue;      // before refunds
        private BigDecimal refundedAmount;    // refunded on this day
        private BigDecimal netRevenue;        // grossRevenue - refundedAmount
        private Long transactionCount;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class InventoryOverview {
        private Long totalProducts;
        private Long lowStockCount;
        private Long outOfStockCount;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RefundSummary {
        private Long totalRefunds;
        private BigDecimal totalRefundedAmount;
    }
}
