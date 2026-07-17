package com.school.app.common.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TenantContextTest {

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    void defaultsToTheNilSentinelWithNothingSet() {
        assertThat(TenantContext.get()).isEqualTo(TenantContext.NIL_SCHOOL_ID);
        assertThat(TenantContext.isSet()).isFalse();
    }

    @Test
    void setThenGetRoundTrips() {
        UUID schoolId = UUID.randomUUID();

        TenantContext.set(schoolId);

        assertThat(TenantContext.get()).isEqualTo(schoolId);
        assertThat(TenantContext.isSet()).isTrue();
    }

    @Test
    void settingNullFallsBackToTheNilSentinelRatherThanNull() {
        TenantContext.set(UUID.randomUUID());

        TenantContext.set(null);

        assertThat(TenantContext.get()).isEqualTo(TenantContext.NIL_SCHOOL_ID);
        assertThat(TenantContext.isSet()).isFalse();
    }

    @Test
    void clearResetsToTheNilSentinel() {
        TenantContext.set(UUID.randomUUID());

        TenantContext.clear();

        assertThat(TenantContext.get()).isEqualTo(TenantContext.NIL_SCHOOL_ID);
        assertThat(TenantContext.isSet()).isFalse();
    }

    @Test
    void nilSentinelIsTheAllZeroUuid() {
        assertThat(TenantContext.NIL_SCHOOL_ID).isEqualTo(new UUID(0L, 0L));
    }

    @Test
    void tenantDoesNotLeakAcrossThreads() throws InterruptedException {
        TenantContext.set(UUID.randomUUID());

        final boolean[] otherThreadSawTenant = {true};
        Thread other = new Thread(() -> otherThreadSawTenant[0] = TenantContext.isSet());
        other.start();
        other.join();

        assertThat(otherThreadSawTenant[0]).isFalse();
    }
}
