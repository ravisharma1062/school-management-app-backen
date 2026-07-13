package com.school.app.platform;

/**
 * A feature or quota a subscription plan can grant. Boolean toggles gate an endpoint/behavior
 * outright; {@link #MAX_STUDENTS} is a quota checked via {@code EntitlementService.checkLimit}
 * instead of {@code isEntitled}.
 */
public enum FeatureKey {
    EMAIL_NOTIFICATIONS,
    SMS_NOTIFICATIONS,
    ONLINE_PAYMENTS,
    MESSAGING,
    TRANSPORT_TRACKING,
    LIBRARY,
    ANALYTICS,
    MAX_STUDENTS
}
