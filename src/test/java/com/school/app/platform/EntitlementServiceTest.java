package com.school.app.platform;

import com.school.app.common.exception.FeatureNotEntitledException;
import com.school.app.common.exception.LimitExceededException;
import com.school.app.common.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntitlementServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private EntitlementRepository entitlementRepository;

    @InjectMocks
    private EntitlementService entitlementService;

    private UUID schoolId;
    private Subscription subscription;

    @BeforeEach
    void setUp() {
        schoolId = UUID.randomUUID();
        subscription = Subscription.builder().id(UUID.randomUUID()).build();
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private void stubEntitlement(FeatureKey key, boolean enabled, Integer limitValue) {
        TenantContext.set(schoolId);
        when(subscriptionRepository.findBySchoolId(schoolId)).thenReturn(Optional.of(subscription));
        when(entitlementRepository.findBySubscriptionIdAndFeatureKey(subscription.getId(), key))
                .thenReturn(Optional.of(Entitlement.builder()
                        .subscription(subscription)
                        .featureKey(key)
                        .enabled(enabled)
                        .limitValue(limitValue)
                        .build()));
    }

    @Test
    void isEntitledReturnsFalseWhenNoTenantIsSet() {
        assertThat(entitlementService.isEntitled(FeatureKey.MESSAGING)).isFalse();
        verifyNoInteractions(subscriptionRepository, entitlementRepository);
    }

    @Test
    void isEntitledReturnsFalseWhenSchoolHasNoSubscription() {
        TenantContext.set(schoolId);
        when(subscriptionRepository.findBySchoolId(schoolId)).thenReturn(Optional.empty());

        assertThat(entitlementService.isEntitled(FeatureKey.MESSAGING)).isFalse();
    }

    @Test
    void isEntitledReturnsFalseWhenEntitlementRowIsMissing() {
        TenantContext.set(schoolId);
        when(subscriptionRepository.findBySchoolId(schoolId)).thenReturn(Optional.of(subscription));
        when(entitlementRepository.findBySubscriptionIdAndFeatureKey(subscription.getId(), FeatureKey.MESSAGING))
                .thenReturn(Optional.empty());

        assertThat(entitlementService.isEntitled(FeatureKey.MESSAGING)).isFalse();
    }

    @Test
    void isEntitledReflectsTheEnabledFlag() {
        stubEntitlement(FeatureKey.MESSAGING, true, null);
        assertThat(entitlementService.isEntitled(FeatureKey.MESSAGING)).isTrue();
    }

    @Test
    void assertEntitledThrowsForADisabledFeature() {
        stubEntitlement(FeatureKey.SMS_NOTIFICATIONS, false, null);

        assertThatThrownBy(() -> entitlementService.assertEntitled(FeatureKey.SMS_NOTIFICATIONS))
                .isInstanceOf(FeatureNotEntitledException.class)
                .hasMessageContaining("SMS_NOTIFICATIONS");
    }

    @Test
    void assertEntitledPassesForAnEnabledFeature() {
        stubEntitlement(FeatureKey.ANALYTICS, true, null);

        assertThatCode(() -> entitlementService.assertEntitled(FeatureKey.ANALYTICS))
                .doesNotThrowAnyException();
    }

    @Test
    void checkLimitAllowsCountsBelowTheQuota() {
        stubEntitlement(FeatureKey.MAX_STUDENTS, true, 150);

        assertThatCode(() -> entitlementService.checkLimit(FeatureKey.MAX_STUDENTS, 149))
                .doesNotThrowAnyException();
    }

    @Test
    void checkLimitRejectsWhenTheQuotaIsAlreadyReached() {
        stubEntitlement(FeatureKey.MAX_STUDENTS, true, 150);

        assertThatThrownBy(() -> entitlementService.checkLimit(FeatureKey.MAX_STUDENTS, 150))
                .isInstanceOf(LimitExceededException.class)
                .hasMessageContaining("150");
    }

    @Test
    void checkLimitTreatsNullLimitAsUnlimited() {
        stubEntitlement(FeatureKey.MAX_STUDENTS, true, null);

        assertThatCode(() -> entitlementService.checkLimit(FeatureKey.MAX_STUDENTS, 1_000_000))
                .doesNotThrowAnyException();
    }

    @Test
    void checkLimitWithoutATenantIsANoOp() {
        assertThatCode(() -> entitlementService.checkLimit(FeatureKey.MAX_STUDENTS, Long.MAX_VALUE))
                .doesNotThrowAnyException();
    }
}
