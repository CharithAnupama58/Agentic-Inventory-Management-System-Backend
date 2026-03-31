package com.pos.system.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "inventory_logs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class InventoryLog {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 30)
    private MovementType movementType;

    @Column(nullable = false)
    private Integer quantity;           // positive = in, negative = out

    @Column(name = "stock_before", nullable = false)
    private Integer stockBefore;

    @Column(name = "stock_after", nullable = false)
    private Integer stockAfter;

    @Column(name = "reference_id")
    private UUID referenceId;           // sale_id or reorder_id

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { this.createdAt = LocalDateTime.now(); }

    public enum MovementType {
        SALE,           // stock reduced by sale
        REFUND,         // stock restored by refund
        RESTOCK,        // manual stock addition
        ADJUSTMENT,     // manual correction
        EXPIRED,        // batch expired
        DAMAGED         // damaged goods written off
    }
}
