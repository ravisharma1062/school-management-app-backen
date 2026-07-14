package com.school.app.platform;

import jakarta.validation.constraints.NotNull;

public record PlatformSettingsUpdateRequest(
        @NotNull Boolean autoApproveSignups
) {
}
