package com.school.app.platform;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EntitlementRepository extends JpaRepository<Entitlement, UUID> {

    List<Entitlement> findBySubscriptionId(UUID subscriptionId);

    Optional<Entitlement> findBySubscriptionIdAndFeatureKey(UUID subscriptionId, FeatureKey featureKey);
}
