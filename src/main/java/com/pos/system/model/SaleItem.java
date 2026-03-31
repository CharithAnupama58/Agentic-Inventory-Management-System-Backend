package com.pos.system.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "sale_items")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SaleItem {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "refunded_quantity")
    @Builder.Default
    private Integer refundedQuantity = 0;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;          // actual price charged (after discount)

    @Column(name = "original_price", precision = 10, scale = 2)
    private BigDecimal originalPrice;  // price before campaign discount

    @Column(name = "campaign_id")
    private UUID campaignId;           // which campaign was applied

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;
}
