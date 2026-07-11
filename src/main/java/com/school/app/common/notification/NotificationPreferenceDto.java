package com.school.app.common.notification;

public record NotificationPreferenceDto(
        NotificationEventType eventType,
        boolean smsEnabled,
        boolean emailEnabled
) {
}
