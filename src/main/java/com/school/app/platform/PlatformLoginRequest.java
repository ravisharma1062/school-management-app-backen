package com.school.app.platform;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record PlatformLoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password,
        /** Required only once the account has enrolled MFA — see {@link PlatformAuthService}. */
        String mfaCode
) {
}
