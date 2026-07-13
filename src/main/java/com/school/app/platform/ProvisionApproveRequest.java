package com.school.app.platform;

import jakarta.validation.constraints.NotNull;

public record ProvisionApproveRequest(
        /** Falls back to the signup request's own {@code desiredPlan} when null. */
        PlanCode planCode,
        @NotNull Boolean startAsTrial
) {
}
