package com.school.app.platform;

public record PlatformAuthResponse(
        String accessToken,
        String refreshToken,
        PlatformRole platformRole,
        boolean mfaEnrolled
) {
}
