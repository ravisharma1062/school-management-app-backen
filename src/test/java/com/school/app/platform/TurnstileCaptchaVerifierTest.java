package com.school.app.platform;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TurnstileCaptchaVerifierTest {

    @Test
    void unconfiguredSecretPassesEverythingThrough() {
        TurnstileCaptchaVerifier verifier = new TurnstileCaptchaVerifier("");

        assertThat(verifier.verify(null, null)).isTrue();
        assertThat(verifier.verify("", "1.2.3.4")).isTrue();
        assertThat(verifier.verify("any-token", "1.2.3.4")).isTrue();
        // Second call exercises the warn-once flag path.
        assertThat(verifier.verify("another-token", null)).isTrue();
    }

    @Test
    void configuredSecretRejectsMissingToken() {
        TurnstileCaptchaVerifier verifier = new TurnstileCaptchaVerifier("some-secret");

        assertThat(verifier.verify(null, "1.2.3.4")).isFalse();
        assertThat(verifier.verify("", "1.2.3.4")).isFalse();
        assertThat(verifier.verify("   ", "1.2.3.4")).isFalse();
    }
}
