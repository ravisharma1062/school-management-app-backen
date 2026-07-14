package com.school.app.platform;

import com.school.app.common.exception.ResourceNotFoundException;
import com.school.app.common.security.TenantContext;
import com.school.app.student.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final EntitlementRepository entitlementRepository;
    private final StudentRepository studentRepository;

    // Reads subscription.getPlan() (a lazy association) after the repository call that loaded
    // it returns — open-in-view is disabled, so that association is only safe to traverse while
    // still inside one transaction spanning the whole method, not the repository call alone.
    @Transactional(readOnly = true)
    public SubscriptionDto getCurrent() {
        Subscription subscription = subscriptionRepository.findBySchoolId(TenantContext.get())
                .orElseThrow(() -> new ResourceNotFoundException("No subscription found for this school"));

        // MT-6c usage metering: MAX_STUDENTS is the only quota feature today, so it's the only one
        // with a meaningful "current usage" to show alongside its limit.
        long activeStudentCount = studentRepository.countByActiveTrue();
        var entitlements = entitlementRepository.findBySubscriptionId(subscription.getId()).stream()
                .map(e -> new EntitlementDto(
                        e.getFeatureKey(), e.isEnabled(), e.getLimitValue(),
                        e.getFeatureKey() == FeatureKey.MAX_STUDENTS ? activeStudentCount : null))
                .toList();

        return new SubscriptionDto(
                subscription.getPlan().getCode(),
                subscription.getPlan().getName(),
                subscription.getStatus(),
                subscription.getTrialEndsAt(),
                subscription.getCurrentPeriodEnd(),
                entitlements);
    }
}
