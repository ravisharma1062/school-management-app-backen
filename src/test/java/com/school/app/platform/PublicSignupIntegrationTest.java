package com.school.app.platform;

import com.school.app.common.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers Phase MT-4's Definition of Done for the public endpoint: accepts only expected fields,
 * rate-limited, rejects duplicates, and (since CAPTCHA_TURNSTILE_SECRET_KEY is unset in this test
 * environment) exercises the "unconfigured CAPTCHA passes everything through" dev-mode behaviour
 * documented on {@link CaptchaVerifier}.
 */
class PublicSignupIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private SignupRequestRepository signupRequestRepository;

    private PublicSignupRequest validRequest(String email) {
        return new PublicSignupRequest(
                "Sunrise Public School", "Meera Contact", email, "+911234567890",
                PlanCode.STANDARD, true, false, "any-token-since-captcha-is-unconfigured");
    }

    @Test
    void validSubmissionCreatesAPendingSignupRequest() {
        String email = "meera-" + UUID.randomUUID() + "@sunrise.example";
        ResponseEntity<Void> response = restTemplate.postForEntity(
                "/api/v1/public/signup-requests", validRequest(email), Void.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        SignupRequest saved = signupRequestRepository.findByContactEmailAndStatus(email, SignupRequestStatus.NEW)
                .orElseThrow();
        assertThat(saved.getSchoolName()).isEqualTo("Sunrise Public School");
        assertThat(saved.getDesiredPlan()).isEqualTo(PlanCode.STANDARD);
        assertThat(saved.isWantsEmail()).isTrue();
        assertThat(saved.isWantsSms()).isFalse();
    }

    @Test
    void duplicatePendingRequestForTheSameEmailIsRejected() {
        String email = "dup-" + UUID.randomUUID() + "@sunrise.example";
        ResponseEntity<Void> first = restTemplate.postForEntity(
                "/api/v1/public/signup-requests", validRequest(email), Void.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> second = restTemplate.postForEntity(
                "/api/v1/public/signup-requests", validRequest(email), String.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void missingRequiredFieldsIsRejectedAsBadRequest() {
        PublicSignupRequest blank = new PublicSignupRequest("", "", "not-an-email", null, null, false, false, "");
        ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/public/signup-requests", blank, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void exceedingTheRateLimitReturns429() {
        // The rate limiter is keyed by caller IP and its bucket lives for the whole test JVM run
        // (PublicSignupRateLimiter is a singleton with no per-test reset) — pin this test to its
        // own fake X-Forwarded-For address (RFC 5737 TEST-NET-3) so it neither collides with nor
        // is affected by the real-loopback calls the other tests in this class make.
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        headers.set("X-Forwarded-For", "203.0.113.77");

        HttpStatusCode lastStatus = HttpStatus.OK;
        for (int i = 0; i < 8; i++) {
            String email = "rate-" + UUID.randomUUID() + "@sunrise.example";
            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/public/signup-requests", HttpMethod.POST,
                    new HttpEntity<>(validRequest(email), headers), String.class);
            lastStatus = response.getStatusCode();
            if (lastStatus == HttpStatus.TOO_MANY_REQUESTS) {
                break;
            }
        }
        assertThat(lastStatus).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }
}
