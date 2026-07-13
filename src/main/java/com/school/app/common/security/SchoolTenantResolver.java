package com.school.app.common.security;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Tells Hibernate which school every {@code @TenantId}-annotated entity query/insert should be
 * scoped to. Reads {@link TenantContext}, which {@link JwtAuthFilter} (and, during login,
 * {@link UserDetailsServiceImpl}) populate per request.
 *
 * <p><b>Only resolved once per Hibernate {@code Session}</b> (at the EntityManager's creation,
 * i.e. at the start of the enclosing {@code @Transactional} method) — calling {@link
 * TenantContext#set} later, mid-transaction, does NOT change what this already-open Session uses
 * for {@code @TenantId} filtering/auto-population (unlike {@code TenantRlsTransactionListener},
 * which re-applies the RLS session variable on demand and so *does* pick up a later change).
 * Code that discovers its tenant partway through a transaction (provisioning a brand-new school;
 * activation, which resolves the tenant from a token) therefore cannot rely on a plain {@code
 * save()}/{@code findById()} for a {@code @TenantId} entity afterward — see the native-query
 * writes in {@code ProvisioningService}/{@code ActivationService} and their {@code UserRepository}
 * methods, which bypass this resolver entirely for that one write, the same way {@code
 * findByIdBypassingTenantFilter} already does for reads.
 */
@Component
public class SchoolTenantResolver implements CurrentTenantIdentifierResolver<UUID> {

    @Override
    public UUID resolveCurrentTenantIdentifier() {
        return TenantContext.get();
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
