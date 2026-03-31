package com.pos.system.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "campaigns")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Campaign {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String name;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false)
    private BigDecimal discountValue;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    private CampaignStatus status;

    @ElementCollection
    @CollectionTable(name = "campaign_products",
            joinColumns = @JoinColumn(name = "campaign_id"))
    @Column(name = "product_id")
    private List<UUID> productIds;

    @Column(name = "created_by_ai")
    private boolean createdByAi;

    @Column(name = "ai_reason", length = 500)
    private String aiReason;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) this.status = CampaignStatus.ACTIVE;
    }

    public enum DiscountType   { PERCENTAGE, FIXED }
    public enum CampaignStatus { ACTIVE, SCHEDULED, EXPIRED, CANCELLED }
}
