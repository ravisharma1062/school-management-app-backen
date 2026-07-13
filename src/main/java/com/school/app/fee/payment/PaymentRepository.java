package com.school.app.fee.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByGatewayOrderId(String gatewayOrderId);

    List<Payment> findByFeeId(UUID feeId);

    /**
     * Bypasses the {@code @TenantId} filter — a native query, not JPQL. Needed for the payment
     * gateway's webhook callback, the one caller with no JWT and therefore no tenant in context
     * yet; {@code gatewayOrderId} is globally unique (assigned by the gateway, enforced by a DB
     * UNIQUE constraint), same reasoning as the login-by-email bootstrap in
     * {@code UserDetailsServiceImpl}. Never use this for anything reachable by an authenticated
     * (tenant-scoped) caller.
     */
    @Query(value = "SELECT * FROM payments WHERE gateway_order_id = :gatewayOrderId", nativeQuery = true)
    Optional<Payment> findByGatewayOrderIdBypassingTenantFilter(String gatewayOrderId);
}
