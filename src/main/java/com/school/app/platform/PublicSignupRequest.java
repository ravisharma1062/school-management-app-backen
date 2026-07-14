package com.school.app.platform;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The only unauthenticated write this app accepts — every field is validated and size-capped. */
public record PublicSignupRequest(
        @NotBlank @Size(max = 255) String schoolName,
        @NotBlank @Size(max = 255) String contactName,
        @NotBlank @Email @Size(max = 255) String contactEmail,
        @Size(max = 20) String contactPhone,
        @NotNull PlanCode desiredPlan,
        boolean wantsEmail,
        boolean wantsSms,
        @NotBlank @Size(max = 4000) String captchaToken
) {
}
