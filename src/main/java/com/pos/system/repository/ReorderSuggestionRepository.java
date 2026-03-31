package com.pos.system.repository;

import com.pos.system.model.ReorderSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReorderSuggestionRepository extends JpaRepository<ReorderSuggestion, UUID> {

    List<ReorderSuggestion> findByTenantIdAndStatusOrderByUrgencyAsc(
            UUID tenantId, ReorderSuggestion.SuggestionStatus status);

    List<ReorderSuggestion> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    // ── @Modifying + @Transactional required for delete queries ──────────────
    @Modifying
    @Transactional
    @Query("DELETE FROM ReorderSuggestion r " +
           "WHERE r.productId = :productId " +
           "AND r.tenantId = :tenantId " +
           "AND r.status = :status")
    void deleteByProductIdAndTenantIdAndStatus(
            @Param("productId") UUID productId,
            @Param("tenantId") UUID tenantId,
            @Param("status") ReorderSuggestion.SuggestionStatus status);
}
