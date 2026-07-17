package com.school.app.platform;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TrialSignupRateLimiterTest {

    private final TrialSignupRateLimiter rateLimiter = new TrialSignupRateLimiter();

    @Test
    void allowsThreeRequestsPerIpThenRejectsTheFourth() {
        for (int i = 0; i < 3; i++) {
            assertThat(rateLimiter.tryConsume("198.51.100.7"))
                    .as("request %d should be allowed", i + 1)
                    .isTrue();
        }
        assertThat(rateLimiter.tryConsume("198.51.100.7")).isFalse();
    }

    @Test
    void bucketsAreIndependentPerIp() {
        for (int i = 0; i < 4; i++) {
            rateLimiter.tryConsume("198.51.100.7");
        }

        assertThat(rateLimiter.tryConsume("198.51.100.8")).isTrue();
    }
}
