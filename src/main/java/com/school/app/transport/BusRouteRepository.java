package com.school.app.transport;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface BusRouteRepository extends JpaRepository<BusRoute, UUID> {

    /**
     * Bypasses the {@code @TenantId} filter — a native query, not JPQL, so Hibernate's
     * tenant-scoping never applies to it. Needed for {@code POST /transport/routes/{id}/location},
     * the one endpoint a bus's GPS device calls with no JWT and therefore no tenant in context yet;
     * {@code id} is a real primary key and already unambiguous regardless of tenant, same reasoning
     * as the login-by-email bootstrap in {@code UserDetailsServiceImpl}. Never use this for
     * anything reachable by an authenticated (tenant-scoped) caller.
     */
    @Query(value = "SELECT * FROM bus_routes WHERE id = :id", nativeQuery = true)
    Optional<BusRoute> findByIdBypassingTenantFilter(UUID id);
}
