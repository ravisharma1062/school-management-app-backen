package com.school.app.platform;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A self-service trial signup — unlike {@link PublicSignupRequest}, this provisions the school
 * immediately (no operator review), so there's no plan picker: every trial starts on
 * {@link PlanCode#BASIC}. Same size-capped/validated shape as the reviewed signup path.
 */
public record PublicTrialSignupRequest(
        @NotBlank @Size(max = 255) String schoolName,
        @NotBlank @Size(max = 255) String contactName,
        @NotBlank @Email @Size(max = 255) String contactEmail,
        @Size(max = 20) String contactPhone,
        boolean wantsEmail,
        boolean wantsSms,
        @NotBlank @Size(max = 4000) String captchaToken
) {
}
