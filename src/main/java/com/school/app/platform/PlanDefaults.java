package com.school.app.platform;

import java.util.Map;

/**
 * Default entitlement set per plan. Not read by anything in MT-2 itself — the only subscription
 * that exists so far (the pre-existing school's) was backfilled directly in {@code V18}, matching
 * its actual pre-entitlement usage rather than a plan default. This is the reference table MT-3's
 * provisioning service will read from when it creates a *new* school's initial subscription.
 */
public final class PlanDefaults {

    public record Default(boolean enabled, Integer limitValue) {
        public static Default on() {
            return new Default(true, null);
        }

        public static Default off() {
            return new Default(false, null);
        }

        public static Default limit(int value) {
            return new Default(true, value);
        }
    }

    private static final Map<FeatureKey, Default> BASIC = Map.of(
            FeatureKey.EMAIL_NOTIFICATIONS, Default.on(),
            FeatureKey.SMS_NOTIFICATIONS, Default.off(),
            FeatureKey.ONLINE_PAYMENTS, Default.off(),
            FeatureKey.MESSAGING, Default.off(),
            FeatureKey.TRANSPORT_TRACKING, Default.off(),
            FeatureKey.LIBRARY, Default.on(),
            FeatureKey.ANALYTICS, Default.off(),
            FeatureKey.MAX_STUDENTS, Default.limit(150),
            FeatureKey.BRANDING, Default.off());

    private static final Map<FeatureKey, Default> STANDARD = Map.of(
            FeatureKey.EMAIL_NOTIFICATIONS, Default.on(),
            FeatureKey.SMS_NOTIFICATIONS, Default.on(),
            FeatureKey.ONLINE_PAYMENTS, Default.on(),
            FeatureKey.MESSAGING, Default.on(),
            FeatureKey.TRANSPORT_TRACKING, Default.off(),
            FeatureKey.LIBRARY, Default.on(),
            FeatureKey.ANALYTICS, Default.on(),
            FeatureKey.MAX_STUDENTS, Default.limit(500),
            FeatureKey.BRANDING, Default.off());

    private static final Map<FeatureKey, Default> PREMIUM = Map.of(
            FeatureKey.EMAIL_NOTIFICATIONS, Default.on(),
            FeatureKey.SMS_NOTIFICATIONS, Default.on(),
            FeatureKey.ONLINE_PAYMENTS, Default.on(),
            FeatureKey.MESSAGING, Default.on(),
            FeatureKey.TRANSPORT_TRACKING, Default.on(),
            FeatureKey.LIBRARY, Default.on(),
            FeatureKey.ANALYTICS, Default.on(),
            FeatureKey.MAX_STUDENTS, Default.on(),
            FeatureKey.BRANDING, Default.on());

    private PlanDefaults() {
    }

    public static Map<FeatureKey, Default> forPlan(PlanCode code) {
        return switch (code) {
            case BASIC -> BASIC;
            case STANDARD -> STANDARD;
            case PREMIUM -> PREMIUM;
        };
    }
}
