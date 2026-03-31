package com.pos.system.repository;

import com.pos.system.model.Campaign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface CampaignRepository extends JpaRepository<Campaign, UUID> {
    List<Campaign> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    List<Campaign> findByTenantIdAndStatus(
            UUID tenantId, Campaign.CampaignStatus status);
}
