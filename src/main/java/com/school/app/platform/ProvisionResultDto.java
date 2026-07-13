package com.school.app.platform;

import java.util.UUID;

public record ProvisionResultDto(
        UUID schoolId,
        String schoolSlug,
        String adminEmail
) {
}
