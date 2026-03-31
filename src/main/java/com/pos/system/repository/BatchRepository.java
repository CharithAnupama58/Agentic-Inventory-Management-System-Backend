package com.pos.system.repository;

import com.pos.system.model.Batch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface BatchRepository extends JpaRepository<Batch, UUID> {

    List<Batch> findByProductIdAndTenantIdOrderByExpiryDateAsc(
            UUID productId, UUID tenantId);

    // Batches expiring within N days
    @Query("SELECT b FROM Batch b " +
           "WHERE b.tenantId = :tenantId " +
           "AND b.expiryDate <= :expiryBefore " +
           "AND b.status = 'ACTIVE' " +
           "ORDER BY b.expiryDate ASC")
    List<Batch> findExpiringBatches(
            @Param("tenantId") UUID tenantId,
            @Param("expiryBefore") LocalDate expiryBefore);

    List<Batch> findByTenantIdAndStatusOrderByExpiryDateAsc(
            UUID tenantId, Batch.BatchStatus status);
}
