package com.school.app.common.security;

import com.school.app.user.UserRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;
    private final TenantRlsTransactionListener tenantRlsTransactionListener;
    private final EntityManager entityManager;

    // Must span both the tenant-resolution step and the findByEmail() below in one transaction:
    // without this, the injected EntityManager used by applyCurrentTenant() is a short-lived
    // session for that one call, unrelated to whatever fresh connection findByEmail() acquires
    // moments later for its own transaction — so the RLS session variable never reaches the
    // connection that matters. ActivationService/ProvisioningService don't need this explicitly
    // because they're themselves @Transactional already, sharing one bound EntityManager throughout.
    @Override
    @Transactional
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // users is now @TenantId-filtered, so a plain JPA lookup returns nothing until the
        // tenant is known. JwtAuthFilter already sets TenantContext from the validated JWT's
        // school_id claim for normal requests, but at login there is no JWT yet — resolve the
        // tenant first via a query that bypasses the Hibernate filter. Emails are globally
        // unique, so this is safe.
        if (!TenantContext.isSet()) {
            UUID schoolId = resolveSchoolIdByEmail(email);
            if (schoolId == null) {
                throw new UsernameNotFoundException("No user found with email " + email);
            }
            TenantContext.set(schoolId);
            // Re-apply the RLS session variable now that the tenant is known — this transaction's
            // begin-of-transaction hook already ran with no tenant set.
            tenantRlsTransactionListener.applyCurrentTenant(entityManager);
            // SchoolTenantResolver only resolves once per Hibernate Session, at this
            // @Transactional method's entry — before the tenant above was known. A plain
            // findByEmail() would still filter by "no tenant" regardless of the TenantContext.set()
            // call above (see that resolver's Javadoc); RLS, just re-applied, is what actually
            // protects this bypass read.
            return userRepository.findByEmailBypassingTenantFilter(email)
                    .orElseThrow(() -> new UsernameNotFoundException("No user found with email " + email));
        }

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("No user found with email " + email));
    }

    /**
     * Bypassing Hibernate's {@code @TenantId} filter with plain JDBC still isn't enough on its
     * own — {@code users} also has forced Postgres row-level security (V17), which every query on
     * that connection must satisfy regardless of how it's issued. Since {@code app.current_school_id}
     * can't be set yet (finding it is the point of this query), this calls a server-side function
     * (V26) that sets the narrow {@code app.pre_auth_lookup} exception, performs the lookup, and
     * resets it — entirely within one atomic statement, so it doesn't depend on this connection's
     * surrounding transaction (e.g. open-in-view) behaving any particular way across statements.
     */
    private UUID resolveSchoolIdByEmail(String email) {
        return jdbcTemplate.queryForObject(
                "SELECT resolve_login_school_id(?)", UUID.class, email);
    }
}
