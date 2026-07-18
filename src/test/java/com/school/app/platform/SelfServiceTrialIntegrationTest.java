package com.school.app.platform;

import com.school.app.auth.AuthResponse;
import com.school.app.auth.LoginRequest;
import com.school.app.common.AbstractIntegrationTest;
import com.school.app.common.exception.DuplicateResourceException;
import com.school.app.common.security.TenantContext;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    private SubscriptionPlanRepository subscriptionPlanRepository;
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
        // subscription.getPlan() is a lazy proxy; the repository call above already closed its
        // session, so reading anything but the (session-independent) identifier off it here would
        // throw LazyInitializationException — look the plan up by that id instead.
        assertThat(subscriptionPlanRepository.findById(subscription.getPlan().getId()).orElseThrow().getCode())
                .isEqualTo(PlanCode.BASIC);
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

        // As with subscription/plan above: this thread's TenantContext is back at the default
        // school by now (the @BeforeEach convention, see AbstractIntegrationTest), but the new
        // admin belongs to the just-provisioned trial school — findByEmail is @TenantId-filtered.
        TenantContext.set(school.getId());
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

    // Reproduces the race PublicTrialSignupService's own pre-check can't close: two near-
    // simultaneous requests for the same new email can both pass existsByEmailBypassingTenantFilter
    // before either commits, so the second one only gets caught at the actual insert. Calling
    // ProvisioningService directly (skipping PublicTrialSignupService's pre-check layer entirely)
    // reproduces that exact window deterministically, without needing real concurrent threads.
    @Test
    void secondProvisionRacingPastTheDuplicateEmailPreCheckIsRejectedNotAnUnhandled500() {
        String email = "race-trial-" + UUID.randomUUID() + "@mapleleaf.example";
        provisioningService.provisionSelfServiceTrial(validRequest(email));

        assertThatThrownBy(() -> provisioningService.provisionSelfServiceTrial(validRequest(email)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("An account with this email already exists.");
    }

    // Same shape of bug as the email race above, but on schools.slug — and unlike that one, this
    // race can't be reproduced with two sequential calls: uniqueSlug()'s check-then-act correctly
    // avoids a collision once the first call's transaction has already committed (it just picks
    // the next free suffix). The actual bug only fires when both transactions' findBySlug reads
    // happen before either has committed, which needs genuine concurrent threads — a CyclicBarrier
    // forces both to reach their read at the same instant, so the second one's insert reliably
    // collides on the DB's UNIQUE constraint instead of the two racing purely by luck.
    @Test
    void concurrentProvisionsWithTheSameSchoolNameRacingPastSlugGenerationRejectOneWithADistinctMessage() throws Exception {
        String schoolName = "Race Slug School " + UUID.randomUUID();
        java.util.concurrent.CyclicBarrier barrier = new java.util.concurrent.CyclicBarrier(2);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.List<java.util.concurrent.Future<Object>> futures = new java.util.ArrayList<>();
            for (char suffix : new char[] {'a', 'b'}) {
                PublicTrialSignupRequest request = new PublicTrialSignupRequest(
                        schoolName, "Kavya Contact", "race-slug-" + suffix + "-" + UUID.randomUUID() + "@mapleleaf.example",
                        "+911234567890", true, false, "any-token-since-captcha-is-unconfigured");
                futures.add(pool.submit(() -> {
                    barrier.await();
                    try {
                        return provisioningService.provisionSelfServiceTrial(request);
                    } catch (Exception e) {
                        return e;
                    }
                }));
            }

            java.util.List<Object> results = new java.util.ArrayList<>();
            for (var future : futures) {
                results.add(future.get());
            }

            long successes = results.stream().filter(r -> r instanceof ProvisioningService.ProvisionOutcome).count();
            long rejections = results.stream().filter(r -> r instanceof DuplicateResourceException).count();
            assertThat(successes).isEqualTo(1);
            assertThat(rejections).isEqualTo(1);

            DuplicateResourceException rejection = (DuplicateResourceException) results.stream()
                    .filter(r -> r instanceof DuplicateResourceException).findFirst().orElseThrow();
            assertThat(rejection).hasMessage("A school with a very similar name was just registered. Please try again in a moment.");
            assertThat(rejection.getCode()).isNull();
        } finally {
            pool.shutdown();
        }
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
