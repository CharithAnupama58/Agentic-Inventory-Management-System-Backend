package com.pos.system.repository;

import com.pos.system.security.TenantContext;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class TenantAwareRepository<T, ID extends Serializable>
        extends SimpleJpaRepository<T, ID> {

    private final EntityManager entityManager;

    public TenantAwareRepository(JpaEntityInformation<T, ?> entityInformation,
                                  EntityManager entityManager) {
        super(entityInformation, entityManager);
        this.entityManager = entityManager;
    }

    @Override
    public List<T> findAll() {
        applyTenantFilter();
        return super.findAll();
    }

    @Override
    public Optional<T> findById(ID id) {
        applyTenantFilter();
        return super.findById(id);
    }

    // ── Activate Hibernate tenant filter ─────────────────────────────────────
    private void applyTenantFilter() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) return;
        try {
            Session session = entityManager.unwrap(Session.class);
            session.enableFilter("tenantFilter")
                   .setParameter("tenantId", tenantId);
        } catch (Exception ignored) {
            // Entity does not declare tenantFilter — safe to skip
        }
    }
}
