package com.pos.system.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "batches")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class Batch {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "batch_number", nullable = false, length = 100)
    private String batchNumber;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "remaining_quantity", nullable = false)
    private Integer remainingQuantity;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    @Column(name = "cost_price", nullable = false)
    private java.math.BigDecimal costPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private BatchStatus status;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.status    = BatchStatus.ACTIVE;
    }

    public enum BatchStatus { ACTIVE, EXPIRED, DEPLETED }
}
