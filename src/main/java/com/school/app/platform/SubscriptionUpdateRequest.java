package com.school.app.platform;

import jakarta.validation.constraints.NotNull;

public record SubscriptionUpdateRequest(
        @NotNull PlanCode planCode
) {
}
