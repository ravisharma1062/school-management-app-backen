package com.school.app.platform;

public record EntitlementDto(
        FeatureKey featureKey,
        boolean enabled,
        Integer limitValue
) {
}
