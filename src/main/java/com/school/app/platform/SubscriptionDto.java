package com.school.app.platform;

import com.school.app.school.SchoolStatus;

import java.time.Instant;
import java.util.List;

public record SubscriptionDto(
        PlanCode planCode,
        String planName,
        SchoolStatus status,
        Instant trialEndsAt,
        Instant currentPeriodEnd,
        List<EntitlementDto> entitlements
) {
}
