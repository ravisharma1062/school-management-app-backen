package com.school.app.platform;

import com.school.app.school.SchoolStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SubscriptionAdminDto(
        UUID schoolId,
        String schoolName,
        PlanCode planCode,
        String planName,
        SchoolStatus status,
        Instant currentPeriodStart,
        Instant currentPeriodEnd,
        Instant trialEndsAt,
        List<EntitlementDto> entitlements
) {
}
