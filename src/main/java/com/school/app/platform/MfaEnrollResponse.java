package com.school.app.platform;

/** The secret is not persisted until {@link MfaConfirmRequest} proves the admin captured it correctly. */
public record MfaEnrollResponse(
        String secret,
        String otpAuthUri
) {
}
