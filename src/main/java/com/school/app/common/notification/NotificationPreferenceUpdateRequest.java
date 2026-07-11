package com.school.app.common.notification;

import jakarta.validation.constraints.NotNull;

public record NotificationPreferenceUpdateRequest(
        @NotNull Boolean smsEnabled,
        @NotNull Boolean emailEnabled
) {
}
