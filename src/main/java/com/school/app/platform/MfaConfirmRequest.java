package com.school.app.platform;

import jakarta.validation.constraints.NotBlank;

public record MfaConfirmRequest(
        @NotBlank String secret,
        @NotBlank String code
) {
}
