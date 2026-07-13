package com.school.app.common.security;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Tells Hibernate which school every {@code @TenantId}-annotated entity query/insert should be
 * scoped to. Reads {@link TenantContext}, which {@link JwtAuthFilter} (and, during login,
 * {@link UserDetailsServiceImpl}) populate per request.
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
