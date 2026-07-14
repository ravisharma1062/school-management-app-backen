package com.school.app.platform;

import com.school.app.auth.AuthResponse;
import com.school.app.auth.LoginRequest;
import com.school.app.common.AbstractIntegrationTest;
import com.school.app.school.School;
import com.school.app.school.SchoolRepository;
import com.school.app.school.SchoolStatus;
import com.school.app.user.User;
import com.school.app.user.UserRepository;
import com.school.app.user.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers Phase MT-6b's Definition of Done: self-service trial provisions instantly (no operator
 * review) and expires correctly (a real {@code trialEndsAt} 14 days out, same as the
 * operator-approved trial path already covered by {@link OperatorProvisioningIntegrationTest}).
 */
class SelfServiceTrialIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private SchoolRepository schoolRepository;
    @Autowired
    private SubscriptionRepository subscriptionRepository;
    @Autowired
    private EntitlementRepository entitlementRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private ActivationTokenRepository activationTokenRepository;
    @Autowired
    private ProvisioningService provisioningService;

    private PublicTrialSignupRequest validRequest(String email) {
        return new PublicTrialSignupRequest(
                "Maple Leaf School", "Kavya Contact", email, "+911234567890",
                true, false, "any-token-since-captcha-is-unconfigured");
    }

    @Test
    void validSubmissionProvisionsATrialSchoolImmediatelyWithNoOperatorReview() {
        String email = "kavya-" + UUID.randomUUID() + "@mapleleaf.example";
        ResponseEntity<ProvisionResultDto> response = restTemplate.postForEntity(
                "/api/v1/public/trial-signups", validRequest(email), ProvisionResultDto.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ProvisionResultDto result = response.getBody();
        assertThat(result).isNotNull();
        assertThat(result.adminEmail()).isEqualTo(email);

        School school = schoolRepository.findById(result.schoolId()).orElseThrow();
        assertThat(school.getName()).isEqualTo("Maple Leaf School");
        assertThat(school.getStatus()).isEqualTo(SchoolStatus.TRIAL);

        Subscription subscription = subscriptionRepository.findBySchoolId(result.schoolId()).orElseThrow();
        assertThat(subscription.getPlan().getCode()).isEqualTo(PlanCode.BASIC);
        assertThat(subscription.getStatus()).isEqualTo(SchoolStatus.TRIAL);
        assertThat(subscription.getTrialEndsAt()).isNotNull();
        long daysUntilTrialEnds = Duration.between(Instant.now(), subscription.getTrialEndsAt()).toDays();
        assertThat(daysUntilTrialEnds).isBetween(13L, 14L);

        var entitlements = entitlementRepository.findBySubscriptionId(subscription.getId());
        assertThat(entitlements).hasSize(FeatureKey.values().length);
        assertThat(entitlements)
                .filteredOn(e -> e.getFeatureKey() == FeatureKey.EMAIL_NOTIFICATIONS)
                .singleElement().satisfies(e -> assertThat(e.isEnabled()).isTrue());
        assertThat(entitlements)
                .filteredOn(e -> e.getFeatureKey() == FeatureKey.SMS_NOTIFICATIONS)
                .singleElement().satisfies(e -> assertThat(e.isEnabled()).isFalse());

        User admin = userRepository.findByEmail(email).orElseThrow();
        assertThat(admin.getStatus()).isEqualTo(UserStatus.PENDING_ACTIVATION);

        assertThat(auditLogRepository.findAll())
                .filteredOn(a -> a.getAction() == AuditAction.TRIAL_SELF_PROVISIONED && result.schoolId().equals(a.getTargetSchoolId()))
                .singleElement()
                .satisfies(a -> assertThat(a.getActor()).isNull());
    }

    @Test
    void duplicateEmailIsRejectedWith409() {
        String email = "dup-trial-" + UUID.randomUUID() + "@mapleleaf.example";
        ResponseEntity<ProvisionResultDto> first = restTemplate.postForEntity(
                "/api/v1/public/trial-signups", validRequest(email), ProvisionResultDto.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> second = restTemplate.postForEntity(
                "/api/v1/public/trial-signups", validRequest(email), String.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void missingRequiredFieldsIsRejectedAsBadRequest() {
        PublicTrialSignupRequest blank = new PublicTrialSignupRequest("", "", "not-an-email", null, false, false, "");
        ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/public/trial-signups", blank, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void exceedingTheRateLimitReturns429() {
        // TrialSignupRateLimiter's bucket (capacity 3/hour) is a singleton with no per-test reset —
        // pin this test to its own fake X-Forwarded-For (RFC 5737 TEST-NET-3) so it neither collides
        // with nor is affected by the other tests in this class.
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Forwarded-For", "203.0.113.88");

        HttpStatusCode lastStatus = HttpStatus.OK;
        for (int i = 0; i < 6; i++) {
            String email = "rate-trial-" + UUID.randomUUID() + "@mapleleaf.example";
            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/public/trial-signups", HttpMethod.POST,
                    new HttpEntity<>(validRequest(email), headers), String.class);
            lastStatus = response.getStatusCode();
            if (lastStatus == HttpStatus.TOO_MANY_REQUESTS) {
                break;
            }
        }
        assertThat(lastStatus).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void fullActivationFlowLetsTheNewAdminLogIn() {
        String email = "activate-trial-" + UUID.randomUUID() + "@mapleleaf.example";
        // Calling the service directly (rather than the HTTP endpoint) is the only way to recover
        // the raw activation token in a test — in production it only ever exists in the emailed
        // link, same reasoning as OperatorProvisioningIntegrationTest's equivalent test.
        ProvisioningService.ProvisionOutcome outcome = provisioningService.provisionSelfServiceTrial(validRequest(email));
        String rawToken = outcome.rawActivationToken();

        ResponseEntity<ActivationInfoDto> infoResponse = restTemplate.getForEntity(
                "/api/v1/auth/activation/" + rawToken, ActivationInfoDto.class);
        assertThat(infoResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(infoResponse.getBody().adminEmail()).isEqualTo(email);

        ActivateAccountRequest activateRequest = new ActivateAccountRequest(rawToken, "BrandNewPass123");
        ResponseEntity<Void> activateResponse = restTemplate.postForEntity("/api/v1/auth/activate", activateRequest, Void.class);
        assertThat(activateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        AuthResponse loginResponse = restTemplate.postForObject(
                "/api/v1/auth/login", new LoginRequest(email, "BrandNewPass123"), AuthResponse.class);
        assertThat(loginResponse).isNotNull();
        assertThat(loginResponse.accessToken()).isNotBlank();

        assertThat(activationTokenRepository.findAll())
                .filteredOn(t -> t.getUserId() != null)
                .anyMatch(t -> t.isUsed());
    }
}
