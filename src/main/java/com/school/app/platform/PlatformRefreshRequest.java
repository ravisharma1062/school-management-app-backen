package com.school.app.platform;

import jakarta.validation.constraints.NotBlank;

public record PlatformRefreshRequest(
        @NotBlank String refreshToken
) {
}
