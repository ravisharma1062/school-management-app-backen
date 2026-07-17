package com.school.app.platform;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PlanDefaultsTest {

    @ParameterizedTest
    @EnumSource(PlanCode.class)
    void everyPlanDefinesADefaultForEveryFeatureKey(PlanCode code) {
        Map<FeatureKey, PlanDefaults.Default> defaults = PlanDefaults.forPlan(code);

        assertThat(defaults.keySet()).containsExactlyInAnyOrder(FeatureKey.values());
    }

    @Test
    void basicPlanCapsStudentsAt150AndDisablesPaidFeatures() {
        Map<FeatureKey, PlanDefaults.Default> basic = PlanDefaults.forPlan(PlanCode.BASIC);

        assertThat(basic.get(FeatureKey.MAX_STUDENTS)).isEqualTo(new PlanDefaults.Default(true, 150));
        assertThat(basic.get(FeatureKey.EMAIL_NOTIFICATIONS).enabled()).isTrue();
        assertThat(basic.get(FeatureKey.LIBRARY).enabled()).isTrue();
        assertThat(basic.get(FeatureKey.SMS_NOTIFICATIONS).enabled()).isFalse();
        assertThat(basic.get(FeatureKey.ONLINE_PAYMENTS).enabled()).isFalse();
        assertThat(basic.get(FeatureKey.MESSAGING).enabled()).isFalse();
        assertThat(basic.get(FeatureKey.TRANSPORT_TRACKING).enabled()).isFalse();
        assertThat(basic.get(FeatureKey.ANALYTICS).enabled()).isFalse();
        assertThat(basic.get(FeatureKey.BRANDING).enabled()).isFalse();
    }

    @Test
    void standardPlanCapsStudentsAt500ButKeepsTransportAndBrandingOff() {
        Map<FeatureKey, PlanDefaults.Default> standard = PlanDefaults.forPlan(PlanCode.STANDARD);

        assertThat(standard.get(FeatureKey.MAX_STUDENTS)).isEqualTo(new PlanDefaults.Default(true, 500));
        assertThat(standard.get(FeatureKey.SMS_NOTIFICATIONS).enabled()).isTrue();
        assertThat(standard.get(FeatureKey.ONLINE_PAYMENTS).enabled()).isTrue();
        assertThat(standard.get(FeatureKey.MESSAGING).enabled()).isTrue();
        assertThat(standard.get(FeatureKey.ANALYTICS).enabled()).isTrue();
        assertThat(standard.get(FeatureKey.TRANSPORT_TRACKING).enabled()).isFalse();
        assertThat(standard.get(FeatureKey.BRANDING).enabled()).isFalse();
    }

    @Test
    void premiumPlanEnablesEverythingWithUnlimitedStudents() {
        Map<FeatureKey, PlanDefaults.Default> premium = PlanDefaults.forPlan(PlanCode.PREMIUM);

        assertThat(premium.values()).allMatch(PlanDefaults.Default::enabled);
        assertThat(premium.get(FeatureKey.MAX_STUDENTS).limitValue()).isNull();
    }

    @Test
    void defaultFactoriesBuildTheExpectedShapes() {
        assertThat(PlanDefaults.Default.on()).isEqualTo(new PlanDefaults.Default(true, null));
        assertThat(PlanDefaults.Default.off()).isEqualTo(new PlanDefaults.Default(false, null));
        assertThat(PlanDefaults.Default.limit(42)).isEqualTo(new PlanDefaults.Default(true, 42));
    }
}
