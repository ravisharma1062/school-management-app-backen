package com.school.app.common.security;

import java.util.UUID;

/**
 * Request-scoped holder for the current tenant (school) id. Set by {@link JwtAuthFilter} (and,
 * during login bootstrapping, by {@link UserDetailsServiceImpl}) and always cleared in a
 * {@code finally} block so a pooled thread never leaks one request's tenant into the next.
 *
 * <p>{@link #NIL_SCHOOL_ID} is the "no tenant set" sentinel rather than {@code null}, so that any
 * tenant-entity access made before a tenant is resolved filters to zero rows instead of throwing
 * or (worse) accidentally matching a row with a null discriminator.
 */
public final class TenantContext {

    public static final UUID NIL_SCHOOL_ID = new UUID(0L, 0L);

    private static final ThreadLocal<UUID> CURRENT = ThreadLocal.withInitial(() -> NIL_SCHOOL_ID);

    private TenantContext() {
    }

    public static void set(UUID schoolId) {
        CURRENT.set(schoolId != null ? schoolId : NIL_SCHOOL_ID);
    }

    public static UUID get() {
        return CURRENT.get();
    }

    public static boolean isSet() {
        return !NIL_SCHOOL_ID.equals(CURRENT.get());
    }

    public static void clear() {
        CURRENT.remove();
    }
}
