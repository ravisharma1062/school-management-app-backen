package com.school.app.common.notification;

import org.springframework.stereotype.Component;

@Component
public class NotificationPreferenceMapper {

    public NotificationPreferenceDto toDto(NotificationPreference preference) {
        return new NotificationPreferenceDto(
                preference.getEventType(),
                preference.isSmsEnabled(),
                preference.isEmailEnabled()
        );
    }
}
