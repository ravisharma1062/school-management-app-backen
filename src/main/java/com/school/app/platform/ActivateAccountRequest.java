package com.school.app.platform;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ActivateAccountRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 8) String newPassword
) {
}
