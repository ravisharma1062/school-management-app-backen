package com.school.app.platform;

import com.school.app.common.exception.ResourceNotFoundException;
import com.school.app.school.School;
import com.school.app.school.SchoolStatus;
import com.school.app.student.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformSubscriptionServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private SubscriptionPlanRepository subscriptionPlanRepository;
    @Mock
    private EntitlementRepository entitlementRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private StudentRepository studentRepository;

    @InjectMocks
    private PlatformSubscriptionService platformSubscriptionService;

    private School school;
    private SubscriptionPlan basicPlan;
    private SubscriptionPlan premiumPlan;
    private Subscription subscription;
    private PlatformUser actor;

    @BeforeEach
    void setUp() {
        school = School.builder().id(UUID.randomUUID()).name("Springfield High").slug("springfield-high")
                .status(SchoolStatus.ACTIVE).build();
        basicPlan = SubscriptionPlan.builder().id(UUID.randomUUID()).code(PlanCode.BASIC).name("Basic")
                .basePrice(BigDecimal.valueOf(999)).billingInterval(BillingInterval.MONTHLY).build();
        premiumPlan = SubscriptionPlan.builder().id(UUID.randomUUID()).code(PlanCode.PREMIUM).name("Premium")
                .basePrice(BigDecimal.valueOf(4999)).billingInterval(BillingInterval.MONTHLY).build();
        subscription = Subscription.builder().id(UUID.randomUUID()).school(school).plan(basicPlan)
                .status(SchoolStatus.ACTIVE).build();
        actor = PlatformUser.builder().id(UUID.randomUUID()).email("operator@school.app")
                .platformRole(PlatformRole.PLATFORM_ADMIN).build();
    }

    @Test
    void getThrowsWhenTheSchoolHasNoSubscription() {
        when(subscriptionRepository.findBySchoolId(school.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> platformSubscriptionService.get(school.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getReturnsPlanAndEntitlementDetailsWithCurrentUsageOnlyForMaxStudents() {
        when(subscriptionRepository.findBySchoolId(school.getId())).thenReturn(Optional.of(subscription));
        when(studentRepository.countActiveBySchoolIdBypassingTenantFilter(school.getId())).thenReturn(77L);
        when(entitlementRepository.findBySubscriptionId(subscription.getId())).thenReturn(List.of(
                Entitlement.builder().subscription(subscription).featureKey(FeatureKey.MAX_STUDENTS).enabled(true).limitValue(150).build(),
                Entitlement.builder().subscription(subscription).featureKey(FeatureKey.MESSAGING).enabled(false).limitValue(null).build()));

        SubscriptionAdminDto dto = platformSubscriptionService.get(school.getId());

        assertThat(dto.planCode()).isEqualTo(PlanCode.BASIC);
        assertThat(dto.entitlements()).hasSize(2);
        EntitlementDto maxStudents = dto.entitlements().stream()
                .filter(e -> e.featureKey() == FeatureKey.MAX_STUDENTS).findFirst().orElseThrow();
        assertThat(maxStudents.currentUsage()).isEqualTo(77L);
        EntitlementDto messaging = dto.entitlements().stream()
                .filter(e -> e.featureKey() == FeatureKey.MESSAGING).findFirst().orElseThrow();
        assertThat(messaging.currentUsage()).isNull();
    }

    @Test
    void updatePlanReplacesAllEntitlementsWithTheNewPlansDefaults() {
        when(subscriptionRepository.findBySchoolId(school.getId())).thenReturn(Optional.of(subscription));
        when(subscriptionPlanRepository.findByCode(PlanCode.PREMIUM)).thenReturn(Optional.of(premiumPlan));
        List<Entitlement> oldEntitlements = List.of(
                Entitlement.builder().id(UUID.randomUUID()).subscription(subscription).featureKey(FeatureKey.MAX_STUDENTS).enabled(true).limitValue(150).build());
        when(entitlementRepository.findBySubscriptionId(subscription.getId())).thenReturn(oldEntitlements);
        when(studentRepository.countActiveBySchoolIdBypassingTenantFilter(school.getId())).thenReturn(10L);

        SubscriptionAdminDto dto = platformSubscriptionService.updatePlan(school.getId(), PlanCode.PREMIUM, actor);

        assertThat(subscription.getPlan()).isEqualTo(premiumPlan);
        verify(subscriptionRepository).save(subscription);
        verify(entitlementRepository).deleteAll(oldEntitlements);
        // One save per FeatureKey, all reflecting PREMIUM's all-enabled defaults.
        ArgumentCaptor<Entitlement> captor = ArgumentCaptor.forClass(Entitlement.class);
        verify(entitlementRepository, org.mockito.Mockito.times(FeatureKey.values().length)).save(captor.capture());
        assertThat(captor.getAllValues()).allMatch(Entitlement::isEnabled);
        verify(auditService).record(eq(actor), eq(AuditAction.SUBSCRIPTION_PLAN_CHANGED), eq(school.getId()),
                contains("from BASIC to PREMIUM"));
        assertThat(dto.planCode()).isEqualTo(PlanCode.PREMIUM);
    }

    @Test
    void updatePlanThrowsForAnUnknownPlanCodeWithoutMutatingTheSubscription() {
        when(subscriptionRepository.findBySchoolId(school.getId())).thenReturn(Optional.of(subscription));
        when(subscriptionPlanRepository.findByCode(PlanCode.PREMIUM)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> platformSubscriptionService.updatePlan(school.getId(), PlanCode.PREMIUM, actor))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(subscription.getPlan()).isEqualTo(basicPlan);
        verify(subscriptionRepository, never()).save(any());
        verify(entitlementRepository, never()).deleteAll(any());
    }

    @Test
    void updatePlanThrowsWhenTheSchoolHasNoSubscription() {
        when(subscriptionRepository.findBySchoolId(school.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> platformSubscriptionService.updatePlan(school.getId(), PlanCode.PREMIUM, actor))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
