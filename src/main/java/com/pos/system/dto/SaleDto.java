package com.pos.system.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class SaleDto {

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SaleRequest {
        @NotNull @NotEmpty
        private List<SaleItemRequest> items;
        private String     discountType;
        private BigDecimal discountValue;
        @NotBlank
        private String     paymentMethod;
        private BigDecimal cashTendered;
        private BigDecimal cashAmount;
        private BigDecimal cardAmount;
        private String     notes;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SaleItemRequest {
        @NotNull private UUID    productId;
        @NotNull @Min(1) private Integer quantity;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SaleItemResponse {
        private UUID       productId;
        private String     productName;
        private Integer    quantity;
        private Integer    refundedQuantity;
        private BigDecimal price;           // actual price charged
        private BigDecimal originalPrice;   // before campaign discount
        private BigDecimal subtotal;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SaleResponse {
        private UUID       id;
        private BigDecimal subtotal;
        private String     discountType;
        private BigDecimal discountValue;
        private BigDecimal discountAmount;
        private BigDecimal totalAmount;
        private BigDecimal totalRefundedAmount;
        private String     paymentMethod;
        private BigDecimal cashAmount;
        private BigDecimal cardAmount;
        private BigDecimal cashTendered;
        private BigDecimal changeAmount;
        private String     status;
        private String     notes;
        private LocalDateTime createdAt;
        private List<SaleItemResponse> items;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RefundRequest {
        @NotNull private UUID saleId;
        @NotNull @NotEmpty
        private List<RefundItem> items;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RefundItem {
        @NotNull private UUID    productId;
        @NotNull @Min(1) private Integer quantity;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RefundResponse {
        private UUID       saleId;
        private BigDecimal refundAmount;
        private String     status;
        private List<String> refundedItems;
    }

    // ── Campaign info for a product ───────────────────────────────────────────
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ProductCampaignInfo {
        private String     campaignId;
        private String     campaignName;
        private String     discountType;
        private BigDecimal discountValue;
        private BigDecimal discountAmount;
        private BigDecimal originalPrice;
        private BigDecimal discountedPrice;
        private String     endDate;
    }
}
