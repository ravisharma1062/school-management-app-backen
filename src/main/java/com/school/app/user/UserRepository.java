package com.school.app.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Page<User> findByRole(Role role, Pageable pageable);

    List<User> findByRole(Role role);

    /** MT-6e — at most one per tenant; enforced by {@code UserService.reassignBillingOwner}. */
    Optional<User> findByBillingOwnerTrue();

    boolean existsByBillingOwnerTrue();

    /**
     * Bypasses the {@code @TenantId} filter — for the account-activation flow, which resolves
     * the tenant from the activation token itself rather than a JWT, mirroring
     * {@code BusRouteRepository.findByIdBypassingTenantFilter}.
     */
    @Query(value = "SELECT * FROM users WHERE id = :id", nativeQuery = true)
    Optional<User> findByIdBypassingTenantFilter(UUID id);

    /**
     * Bypasses the {@code @TenantId} filter — for login. {@code UserDetailsServiceImpl} discovers
     * the tenant mid-method (there's no JWT yet to seed it before the Session opens), so per
     * {@link com.school.app.common.security.SchoolTenantResolver}'s Javadoc, a plain {@code
     * findByEmail} would still filter by whatever tenant was current when the enclosing
     * {@code @Transactional} method's Session was created — i.e. none. RLS (not this bypass) is
     * what actually protects this read; {@code TenantRlsTransactionListener.applyCurrentTenant}
     * must already have been called with the resolved tenant before this runs.
     */
    @Query(value = "SELECT * FROM users WHERE email = :email", nativeQuery = true)
    Optional<User> findByEmailBypassingTenantFilter(String email);

    /**
     * Bypasses the {@code @TenantId} filter — for MT-6b's self-service trial signup, a public
     * (no-JWT, no-tenant) caller that must check the {@code email} column's cross-tenant UNIQUE
     * constraint (V1) before insert, since the normal {@link #existsByEmail} would only see
     * whatever single tenant the unauthenticated request's Session happens to resolve to.
     */
    @Query(value = "SELECT EXISTS(SELECT 1 FROM users WHERE email = :email)", nativeQuery = true)
    boolean existsByEmailBypassingTenantFilter(String email);

    /**
     * Bypasses Hibernate's {@code @TenantId} auto-population — for {@code ProvisioningService},
     * which creates this row for a school that didn't exist when its transaction's Session was
     * opened, so {@link com.school.app.common.security.SchoolTenantResolver} would otherwise
     * still resolve to whatever tenant was current at transaction-begin (see its Javadoc), not the
     * new school. {@code TenantRlsTransactionListener.applyCurrentTenant} must still be called
     * first so the RLS policy on this INSERT passes.
     */
    @Modifying
    @Query(value = "INSERT INTO users (id, school_id, name, email, password_hash, role, preferred_language, status, is_billing_owner, created_at) "
            + "VALUES (:id, :schoolId, :name, :email, :passwordHash, :role, :preferredLanguage, :status, true, now())", nativeQuery = true)
    void insertBypassingTenantFilter(
            @Param("id") UUID id,
            @Param("schoolId") UUID schoolId,
            @Param("name") String name,
            @Param("email") String email,
            @Param("passwordHash") String passwordHash,
            @Param("role") String role,
            @Param("preferredLanguage") String preferredLanguage,
            @Param("status") String status);

    /** The write-side counterpart of {@link #findByIdBypassingTenantFilter} — same reasoning as {@link #insertBypassingTenantFilter}. */
    @Modifying
    @Query(value = "UPDATE users SET password_hash = :passwordHash, status = :status WHERE id = :id", nativeQuery = true)
    int activateBypassingTenantFilter(@Param("id") UUID id, @Param("passwordHash") String passwordHash, @Param("status") String status);
}
