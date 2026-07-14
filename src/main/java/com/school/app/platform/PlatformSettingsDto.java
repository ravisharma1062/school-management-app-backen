package com.school.app.platform;

public record PlatformSettingsDto(
        boolean autoApproveSignups,
        String paymentInstructions
) {
}
