package com.pos.system.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "reorder_suggestions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class ReorderSuggestion {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "current_stock", nullable = false)
    private Integer currentStock;

    @Column(name = "reorder_point", nullable = false)
    private Integer reorderPoint;         // trigger level

    @Column(name = "suggested_quantity", nullable = false)
    private Integer suggestedQuantity;    // how much to order

    @Column(name = "avg_daily_sales", precision = 10, scale = 2)
    private BigDecimal avgDailySales;     // calculated from history

    @Column(name = "days_of_stock_left")
    private Integer daysOfStockLeft;      // at current sales rate

    @Enumerated(EnumType.STRING)
    @Column(name = "urgency", length = 20)
    private Urgency urgency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private SuggestionStatus status;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.status    = SuggestionStatus.PENDING;
    }

    public enum Urgency    { CRITICAL, HIGH, MEDIUM, LOW }
    public enum SuggestionStatus { PENDING, ACKNOWLEDGED, ORDERED, DISMISSED }
}
