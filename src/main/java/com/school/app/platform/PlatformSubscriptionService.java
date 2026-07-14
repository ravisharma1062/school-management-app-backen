package com.school.app.platform;

import com.school.app.common.exception.ResourceNotFoundException;
import com.school.app.student.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * The platform-side view of a school's subscription — distinct from the tenant-facing
 * {@link SubscriptionController} ({@code GET /api/v1/subscription}, ADMIN-only, self-service-read).
 * This is operator-only and, on {@link #updatePlan}, mutating: "modify email/SMS later" works
 * operationally today by changing plan here, ahead of MT-5's self-service billing.
 */
@Service
@RequiredArgsConstructor
public class PlatformSubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final EntitlementRepository entitlementRepository;
    private final AuditService auditService;
    private final StudentRepository studentRepository;

    @Transactional(readOnly = true)
    public SubscriptionAdminDto get(UUID schoolId) {
        return toDto(requireSubscription(schoolId));
    }

    @Transactional
    public SubscriptionAdminDto updatePlan(UUID schoolId, PlanCode newPlanCode, PlatformUser actor) {
        Subscription subscription = requireSubscription(schoolId);
        SubscriptionPlan newPlan = subscriptionPlanRepository.findByCode(newPlanCode)
                .orElseThrow(() -> new ResourceNotFoundException("Plan " + newPlanCode + " not found"));
        PlanCode oldPlanCode = subscription.getPlan().getCode();

        subscription.setPlan(newPlan);
        subscriptionRepository.save(subscription);

        entitlementRepository.deleteAll(entitlementRepository.findBySubscriptionId(subscription.getId()));
        var defaults = PlanDefaults.forPlan(newPlanCode);
        for (FeatureKey key : FeatureKey.values()) {
            var planDefault = defaults.get(key);
            entitlementRepository.save(Entitlement.builder()
                    .subscription(subscription)
                    .featureKey(key)
                    .enabled(planDefault.enabled())
                    .limitValue(planDefault.limitValue())
                    .build());
        }

        auditService.record(actor, AuditAction.SUBSCRIPTION_PLAN_CHANGED, schoolId,
                "Changed plan for '" + subscription.getSchool().getName() + "' from " + oldPlanCode + " to " + newPlanCode);
        return toDto(subscription);
    }

    private Subscription requireSubscription(UUID schoolId) {
        return subscriptionRepository.findBySchoolId(schoolId)
                .orElseThrow(() -> new ResourceNotFoundException("No subscription found for school " + schoolId));
    }

    private SubscriptionAdminDto toDto(Subscription subscription) {
        UUID schoolId = subscription.getSchool().getId();
        long activeStudentCount = studentRepository.countActiveBySchoolIdBypassingTenantFilter(schoolId);
        var entitlements = entitlementRepository.findBySubscriptionId(subscription.getId()).stream()
                .map(e -> new EntitlementDto(
                        e.getFeatureKey(), e.isEnabled(), e.getLimitValue(),
                        e.getFeatureKey() == FeatureKey.MAX_STUDENTS ? activeStudentCount : null))
                .toList();
        return new SubscriptionAdminDto(
                subscription.getSchool().getId(),
                subscription.getSchool().getName(),
                subscription.getPlan().getCode(),
                subscription.getPlan().getName(),
                subscription.getStatus(),
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd(),
                subscription.getTrialEndsAt(),
                entitlements);
    }
}
