package com.school.app.platform;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PublicSignupRateLimiterTest {

    private final PublicSignupRateLimiter rateLimiter = new PublicSignupRateLimiter();

    @Test
    void allowsFiveRequestsPerIpThenRejectsTheSixth() {
        for (int i = 0; i < 5; i++) {
            assertThat(rateLimiter.tryConsume("203.0.113.1"))
                    .as("request %d should be allowed", i + 1)
                    .isTrue();
        }
        assertThat(rateLimiter.tryConsume("203.0.113.1")).isFalse();
    }

    @Test
    void bucketsAreIndependentPerIp() {
        for (int i = 0; i < 6; i++) {
            rateLimiter.tryConsume("203.0.113.1");
        }

        assertThat(rateLimiter.tryConsume("203.0.113.2")).isTrue();
    }
}
