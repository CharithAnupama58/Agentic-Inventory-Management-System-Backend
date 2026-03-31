package com.pos.system.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "sales")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class Sale {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "subtotal", nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "discount_type", length = 20)
    private String discountType;

    @Column(name = "discount_value", precision = 10, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "discount_amount", precision = 10, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    // ── Tracks how much has been refunded so far ──────────────────────────────
    @Column(name = "total_refunded_amount", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal totalRefundedAmount = BigDecimal.ZERO;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @Column(name = "cash_amount", precision = 10, scale = 2)
    private BigDecimal cashAmount;

    @Column(name = "card_amount", precision = 10, scale = 2)
    private BigDecimal cardAmount;

    @Column(name = "cash_tendered", precision = 10, scale = 2)
    private BigDecimal cashTendered;

    @Column(name = "change_amount", precision = 10, scale = 2)
    private BigDecimal changeAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private SaleStatus status;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SaleItem> items = new ArrayList<>();

    @PrePersist
    protected void onCreate() { this.createdAt = LocalDateTime.now(); }

    public enum SaleStatus { COMPLETED, REFUNDED, PARTIALLY_REFUNDED }
}
