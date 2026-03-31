package com.pos.system.repository;

import jakarta.persistence.EntityManager;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactoryBean;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.data.repository.core.RepositoryInformation;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;

import java.io.Serializable;

public class TenantAwareRepositoryFactoryBean<R extends JpaRepository<T, I>, T, I extends Serializable>
        extends JpaRepositoryFactoryBean<R, T, I> {

    public TenantAwareRepositoryFactoryBean(Class<? extends R> repositoryInterface) {
        super(repositoryInterface);
    }

    @Override
    protected RepositoryFactorySupport createRepositoryFactory(EntityManager em) {
        return new TenantAwareRepositoryFactory(em);
    }

    private static class TenantAwareRepositoryFactory extends JpaRepositoryFactory {

        private final EntityManager entityManager;

        TenantAwareRepositoryFactory(EntityManager entityManager) {
            super(entityManager);
            this.entityManager = entityManager;
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        protected SimpleJpaRepository<?, ?> getTargetRepository(
                RepositoryInformation information, EntityManager em) {
            JpaEntityInformation entityInfo =
                    getEntityInformation(information.getDomainType());
            return new TenantAwareRepository<>(entityInfo, entityManager);
        }

        @Override
        protected Class<?> getRepositoryBaseClass(RepositoryMetadata metadata) {
            return TenantAwareRepository.class;
        }
    }
}
