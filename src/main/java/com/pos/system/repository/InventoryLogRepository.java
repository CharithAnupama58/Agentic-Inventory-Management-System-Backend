package com.pos.system.repository;

import com.pos.system.model.InventoryLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface InventoryLogRepository extends JpaRepository<InventoryLog, UUID> {

    List<InventoryLog> findByProductIdAndTenantIdOrderByCreatedAtDesc(
            UUID productId, UUID tenantId);

    List<InventoryLog> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    // Total units sold per product in last N days
    @Query("SELECT il.productId, SUM(il.quantity) FROM InventoryLog il " +
           "WHERE il.tenantId = :tenantId " +
           "AND il.movementType = 'SALE' " +
           "AND il.createdAt >= :since " +
           "GROUP BY il.productId")
    List<Object[]> findSalesSince(
            @Param("tenantId") UUID tenantId,
            @Param("since") LocalDateTime since);
}
