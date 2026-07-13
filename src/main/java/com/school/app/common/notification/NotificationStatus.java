package com.school.app.common.notification;

public enum NotificationStatus {
    SENT,
    FAILED,
    /** The channel was enabled but its provider has no credentials configured yet. */
    SKIPPED,
    /** The channel isn't included in the school's current subscription plan. */
    SKIPPED_NOT_ENTITLED
}
