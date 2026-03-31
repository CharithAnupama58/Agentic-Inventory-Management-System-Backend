package com.pos.system.repository;

import com.pos.system.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    // ── Non-paginated (for POS screen and internal use) ───────────────────────
    List<Product> findAllByTenantId(UUID tenantId);

    Optional<Product> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByBarcodeAndTenantId(String barcode, UUID tenantId);

    // ── Paginated ─────────────────────────────────────────────────────────────
    Page<Product> findAllByTenantId(UUID tenantId, Pageable pageable);

    // ── Search by name or barcode with pagination ─────────────────────────────
    @Query("SELECT p FROM Product p " +
           "WHERE p.tenantId = :tenantId " +
           "AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR p.barcode LIKE CONCAT('%', :search, '%'))")
    Page<Product> searchByTenantId(
            @Param("tenantId") UUID tenantId,
            @Param("search") String search,
            Pageable pageable);

    // ── Low stock products ────────────────────────────────────────────────────
    @Query("SELECT p FROM Product p " +
           "WHERE p.tenantId = :tenantId " +
           "AND p.stock <= :threshold " +
           "ORDER BY p.stock ASC")
    List<Product> findLowStockByTenantId(
            @Param("tenantId") UUID tenantId,
            @Param("threshold") int threshold);
}
