package com.school.app.platform;

import com.school.app.common.exception.FeatureNotEntitledException;
import com.school.app.common.exception.LimitExceededException;
import com.school.app.common.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Single point of truth for "can the current tenant use feature X" / "has it hit its quota for
 * X" — every caller (endpoints via {@link RequiresEntitlement}, {@code NotificationServiceImpl},
 * student-creation limit checks) goes through here instead of inlining its own boolean check.
 *
 * <p>Resolves against {@link TenantContext}, so it's already correctly scoped by the time any
 * service calls it — no school id parameter to (mis)pass around.
 */
@Service
@RequiredArgsConstructor
public class EntitlementService {

    private final SubscriptionRepository subscriptionRepository;
    private final EntitlementRepository entitlementRepository;

    public boolean isEntitled(FeatureKey featureKey) {
        return currentEntitlement(featureKey)
                .map(Entitlement::isEnabled)
                .orElse(false);
    }

    public void assertEntitled(FeatureKey featureKey) {
        if (!isEntitled(featureKey)) {
            throw new FeatureNotEntitledException(
                    "This school's plan does not include " + featureKey.name());
        }
    }

    /**
     * @param currentCount the count to check against the quota, e.g. active students so far.
     */
    public void checkLimit(FeatureKey featureKey, long currentCount) {
        Integer limit = currentEntitlement(featureKey)
                .map(Entitlement::getLimitValue)
                .orElse(null);
        if (limit != null && currentCount >= limit) {
            throw new LimitExceededException(
                    "This school's plan allows at most " + limit + " for " + featureKey.name());
        }
    }

    private Optional<Entitlement> currentEntitlement(FeatureKey featureKey) {
        if (!TenantContext.isSet()) {
            return Optional.empty();
        }
        return subscriptionRepository.findBySchoolId(TenantContext.get())
                .flatMap(subscription ->
                        entitlementRepository.findBySubscriptionIdAndFeatureKey(subscription.getId(), featureKey));
    }
}
