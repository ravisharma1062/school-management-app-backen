package com.school.app.platform;

public record EntitlementDto(
        FeatureKey featureKey,
        boolean enabled,
        Integer limitValue,
        /** MT-6c usage metering — populated only for quota features (currently just MAX_STUDENTS). */
        Long currentUsage
) {
}
