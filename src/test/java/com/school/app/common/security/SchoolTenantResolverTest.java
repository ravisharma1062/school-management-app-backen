package com.school.app.common.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SchoolTenantResolverTest {

    private final SchoolTenantResolver resolver = new SchoolTenantResolver();

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void resolvesToTheNilSentinelWhenNoTenantIsSet() {
        assertThat(resolver.resolveCurrentTenantIdentifier()).isEqualTo(TenantContext.NIL_SCHOOL_ID);
    }

    @Test
    void resolvesToWhateverTenantContextCurrentlyHolds() {
        UUID schoolId = UUID.randomUUID();
        TenantContext.set(schoolId);

        assertThat(resolver.resolveCurrentTenantIdentifier()).isEqualTo(schoolId);
    }

    @Test
    void alwaysValidatesExistingSessions() {
        assertThat(resolver.validateExistingCurrentSessions()).isTrue();
    }
}
