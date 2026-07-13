package com.school.app.common.security;

import jakarta.persistence.EntityManager;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;

import java.io.Serializable;
import java.util.Optional;

/**
 * Global replacement for Spring Data JPA's default repository implementation, wired in via
 * {@code @EnableJpaRepositories(repositoryBaseClass = ...)} on {@code SchoolAppApplication}.
 *
 * <p>{@code SimpleJpaRepository.findById()} calls {@code EntityManager.find()} ("load by
 * primary key"), which is a well-documented Hibernate gap (HHH-16179 / HHH-16626): {@code
 * @TenantId} restricts HQL/JPQL/Criteria query execution but NOT primary-key load access, so a
 * plain {@code repository.findById(otherSchoolsId)} silently returns the row regardless of the
 * current tenant. Every domain service in this app uses {@code findById(...).orElseThrow(...)}
 * for get/update/archive/delete, so this single override is what actually makes that pattern
 * tenant-safe everywhere, instead of requiring every one of the ~50 call sites (or all 23
 * repositories) to be individually rewritten.
 */
public class TenantSafeRepositoryImpl<T, ID extends Serializable> extends SimpleJpaRepository<T, ID> {

    private final JpaEntityInformation<T, ?> entityInformation;
    private final EntityManager entityManager;

    public TenantSafeRepositoryImpl(JpaEntityInformation<T, ?> entityInformation, EntityManager entityManager) {
        super(entityInformation, entityManager);
        this.entityInformation = entityInformation;
        this.entityManager = entityManager;
    }

    @Override
    public Optional<T> findById(ID id) {
        String idAttribute = entityInformation.getIdAttribute().getName();
        String jpql = "SELECT e FROM " + entityInformation.getEntityName() + " e WHERE e." + idAttribute + " = :id";
        return entityManager.createQuery(jpql, entityInformation.getJavaType())
                .setParameter("id", id)
                .getResultStream()
                .findFirst();
    }
}
