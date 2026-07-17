package com.school.app.billing;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentClaimRepository extends JpaRepository<PaymentClaim, UUID> {

    /** Tenant-scoped (the normal @TenantId filter applies) — a school's own claim history. */
    Page<PaymentClaim> findAllByOrderBySubmittedAtDesc(Pageable pageable);

    /**
     * Bypasses the {@code @TenantId} filter — for the platform verification queue, whose caller
     * (a {@code PlatformUser}) carries no tenant at all, mirroring
     * {@code BusRouteRepository.findByIdBypassingTenantFilter}.
     */
    @Query(value = "SELECT * FROM payment_claims WHERE status = :status ORDER BY submitted_at ASC", nativeQuery = true)
    List<PaymentClaim> findAllByStatusBypassingTenantFilter(@Param("status") String status);

    @Query(value = "SELECT * FROM payment_claims WHERE id = :id", nativeQuery = true)
    Optional<PaymentClaim> findByIdBypassingTenantFilter(@Param("id") UUID id);

    /**
     * The write-side counterpart — an UPDATE-by-id native query bypasses any ambiguity about
     * whether Hibernate's {@code @TenantId} session filter would apply to a fetch-then-save on an
     * entity a platform (no-tenant) caller doesn't itself belong to, same reasoning as
     * {@code UserRepository.activateBypassingTenantFilter}.
     * <p>
     * {@code clearAutomatically = true} matters here: {@code PlatformPaymentService.verify()}/
     * {@code reject()} both load the claim via {@code findByIdBypassingTenantFilter} (populating
     * the first-level cache with the pre-update entity) before calling this update, then re-load
     * it by id again afterward to build the response DTO. Without clearing the persistence
     * context, that second load returns the stale cached instance instead of the freshly-updated
     * row — a native bulk UPDATE never touches Hibernate's session cache on its own.
     */
    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE payment_claims SET status = :status, verified_by = :verifiedBy, verified_at = :verifiedAt, notes = :notes WHERE id = :id",
            nativeQuery = true)
    int updateVerificationBypassingTenantFilter(
            @Param("id") UUID id,
            @Param("status") String status,
            @Param("verifiedBy") UUID verifiedBy,
            @Param("verifiedAt") Instant verifiedAt,
            @Param("notes") String notes);
}
