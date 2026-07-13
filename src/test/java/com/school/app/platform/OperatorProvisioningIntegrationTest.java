package com.school.app.platform;

import com.school.app.auth.AuthResponse;
import com.school.app.auth.LoginRequest;
import com.school.app.common.AbstractIntegrationTest;
import com.school.app.school.SchoolRepository;
import com.school.app.school.SchoolStatus;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers Phase MT-3's Definition of Done: platform-scope token separation, atomic provisioning,
 * single-use activation, MFA enrolment, and audit logging. The seeded platform admin
 * (operator@school.app / Operator@123, from V19) has no MFA enrolled, matching a fresh deploy.
 */
class OperatorProvisioningIntegrationTest extends AbstractIntegrationTest {

    private static final String PLATFORM_EMAIL = "operator@school.app";
    private static final String PLATFORM_PASSWORD = "Operator@123";

    @Autowired
    private SignupRequestRepository signupRequestRepository;
    @Autowired
    private SchoolRepository schoolRepository;
    @Autowired
    private SubscriptionRepository subscriptionRepository;
    @Autowired
    private EntitlementRepository entitlementRepository;
    @Autowired
    private ActivationTokenRepository activationTokenRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private PlatformUserRepository platformUserRepository;
    @Autowired
    private ProvisioningService provisioningService;

    private String platformLogin() {
        PlatformLoginRequest request = new PlatformLoginRequest(PLATFORM_EMAIL, PLATFORM_PASSWORD, null);
        ResponseEntity<PlatformAuthResponse> response = restTemplate.postForEntity(
                "/api/v1/platform/auth/login", request, PlatformAuthResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().accessToken();
    }

    private HttpHeaders platformHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(platformLogin());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private String schoolAdminLogin() {
        AuthResponse response = restTemplate.postForObject(
                "/api/v1/auth/login", new LoginRequest("admin@school.app", "Admin@123"), AuthResponse.class);
        return response.accessToken();
    }

    @Test
    void platformTokenCannotHitTenantEndpointsAndTenantTokenCannotHitPlatformEndpoints() {
        HttpHeaders platform = new HttpHeaders();
        platform.setBearerAuth(platformLogin());
        ResponseEntity<String> platformOnTenant = restTemplate.exchange(
                "/api/v1/auth/me", HttpMethod.GET, new HttpEntity<>(platform), String.class);
        assertThat(platformOnTenant.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        HttpHeaders tenant = new HttpHeaders();
        tenant.setBearerAuth(schoolAdminLogin());
        ResponseEntity<String> tenantOnPlatform = restTemplate.exchange(
                "/api/v1/platform/schools", HttpMethod.GET, new HttpEntity<>(tenant), String.class);
        assertThat(tenantOnPlatform.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void wrongPasswordIsRejected() {
        PlatformLoginRequest request = new PlatformLoginRequest(PLATFORM_EMAIL, "wrong-password", null);
        ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/platform/auth/login", request, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void approvingViaHttpAtomicallyProvisionsWithEntitlementsAndBlocksReapproval() {
        SignupRequest signupRequest = signupRequestRepository.save(SignupRequest.builder()
                .schoolName("Riverside Academy " + UUID.randomUUID())
                .contactName("Priya Admin")
                .contactEmail("priya-" + UUID.randomUUID() + "@riverside.example")
                .contactPhone("+911234500000")
                .desiredPlan(PlanCode.STANDARD)
                .wantsEmail(true)
                .wantsSms(false)
                .build());

        HttpHeaders headers = platformHeaders();
        ProvisionApproveRequest approveRequest = new ProvisionApproveRequest(null, true);
        ResponseEntity<ProvisionResultDto> approveResponse = restTemplate.exchange(
                "/api/v1/platform/signup-requests/" + signupRequest.getId() + "/approve",
                HttpMethod.POST, new HttpEntity<>(approveRequest, headers), ProvisionResultDto.class);
        assertThat(approveResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        ProvisionResultDto result = approveResponse.getBody();
        assertThat(result).isNotNull();

        // School + subscription + full entitlement set were created, honouring wantsEmail/wantsSms.
        var school = schoolRepository.findById(result.schoolId()).orElseThrow();
        assertThat(school.getStatus()).isEqualTo(SchoolStatus.TRIAL);
        var subscription = subscriptionRepository.findBySchoolId(result.schoolId()).orElseThrow();
        // Fetched over HTTP (not the lazy-loaded entity directly) — open-in-view is disabled, so
        // subscription.getPlan() is a proxy with no Session by the time this test method runs.
        ResponseEntity<SubscriptionAdminDto> subResponse = restTemplate.exchange(
                "/api/v1/platform/subscriptions/" + result.schoolId(), HttpMethod.GET, new HttpEntity<>(headers), SubscriptionAdminDto.class);
        assertThat(subResponse.getBody().planCode()).isEqualTo(PlanCode.STANDARD);
        var entitlements = entitlementRepository.findBySubscriptionId(subscription.getId());
        assertThat(entitlements).hasSize(FeatureKey.values().length);
        assertThat(entitlements)
                .filteredOn(e -> e.getFeatureKey() == FeatureKey.EMAIL_NOTIFICATIONS)
                .singleElement().satisfies(e -> assertThat(e.isEnabled()).isTrue());
        assertThat(entitlements)
                .filteredOn(e -> e.getFeatureKey() == FeatureKey.SMS_NOTIFICATIONS)
                .singleElement().satisfies(e -> assertThat(e.isEnabled()).isFalse());

        // A single-use activation token was minted for the pending admin.
        var token = activationTokenRepository.findAll().stream()
                .filter(t -> t.getSchoolId().equals(result.schoolId()))
                .findFirst().orElseThrow();
        assertThat(token.isUsed()).isFalse();

        // Re-approving the same (now-APPROVED) request is rejected.
        ResponseEntity<String> secondApprove = restTemplate.exchange(
                "/api/v1/platform/signup-requests/" + signupRequest.getId() + "/approve",
                HttpMethod.POST, new HttpEntity<>(approveRequest, headers), String.class);
        assertThat(secondApprove.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Audit log recorded the provisioning action.
        assertThat(auditLogRepository.findAll())
                .filteredOn(a -> a.getAction() == AuditAction.SIGNUP_REQUEST_APPROVED && result.schoolId().equals(a.getTargetSchoolId()))
                .hasSize(1);

        // The new admin cannot log in yet (PENDING_ACTIVATION → unusable password).
        ResponseEntity<String> loginBeforeActivation = restTemplate.postForEntity(
                "/api/v1/auth/login", new LoginRequest(result.adminEmail(), "whatever"), String.class);
        assertThat(loginBeforeActivation.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void fullActivationFlowLetsTheNewAdminLogInAndTokenIsThenSingleUse() {
        SignupRequest signupRequest = signupRequestRepository.save(SignupRequest.builder()
                .schoolName("Lakeside School " + UUID.randomUUID())
                .contactName("Arjun Admin")
                .contactEmail("arjun-" + UUID.randomUUID() + "@lakeside.example")
                .desiredPlan(PlanCode.BASIC)
                .wantsEmail(true)
                .wantsSms(false)
                .build());
        PlatformUser actor = platformUserRepository.findByEmail(PLATFORM_EMAIL).orElseThrow();

        // Calling the service directly (rather than the HTTP /approve endpoint) is the only way to
        // recover the raw activation token in a test — in production it only ever exists in the
        // emailed link, which is exactly the point of hashing it before storage.
        ProvisioningService.ProvisionOutcome outcome = provisioningService.approve(
                signupRequest.getId(), new ProvisionApproveRequest(null, true), actor);
        String rawToken = outcome.rawActivationToken();
        String adminEmail = outcome.result().adminEmail();

        ResponseEntity<ActivationInfoDto> infoResponse = restTemplate.getForEntity(
                "/api/v1/auth/activation/" + rawToken, ActivationInfoDto.class);
        assertThat(infoResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(infoResponse.getBody().adminEmail()).isEqualTo(adminEmail);
        assertThat(infoResponse.getBody().schoolName()).isEqualTo(signupRequest.getSchoolName());

        ActivateAccountRequest activateRequest = new ActivateAccountRequest(rawToken, "BrandNewPass123");
        ResponseEntity<Void> activateResponse = restTemplate.postForEntity("/api/v1/auth/activate", activateRequest, Void.class);
        assertThat(activateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        AuthResponse loginResponse = restTemplate.postForObject(
                "/api/v1/auth/login", new LoginRequest(adminEmail, "BrandNewPass123"), AuthResponse.class);
        assertThat(loginResponse).isNotNull();
        assertThat(loginResponse.accessToken()).isNotBlank();

        // The token is single-use: activating again with the same raw token now fails.
        ResponseEntity<String> reuseResponse = restTemplate.postForEntity("/api/v1/auth/activate", activateRequest, String.class);
        assertThat(reuseResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<String> invalidActivationInfo = restTemplate.getForEntity(
                "/api/v1/auth/activation/not-a-real-token", String.class);
        assertThat(invalidActivationInfo.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectingASignupRequestAuditsAndBlocksLaterApproval() {
        SignupRequest signupRequest = signupRequestRepository.save(SignupRequest.builder()
                .schoolName("Rejected School " + UUID.randomUUID())
                .contactName("Someone")
                .contactEmail("someone-" + UUID.randomUUID() + "@example.com")
                .desiredPlan(PlanCode.BASIC)
                .wantsEmail(true)
                .wantsSms(false)
                .build());

        HttpHeaders headers = platformHeaders();
        ResponseEntity<Void> rejectResponse = restTemplate.exchange(
                "/api/v1/platform/signup-requests/" + signupRequest.getId() + "/reject",
                HttpMethod.POST, new HttpEntity<>(headers), Void.class);
        assertThat(rejectResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(signupRequestRepository.findById(signupRequest.getId()).orElseThrow().getStatus())
                .isEqualTo(SignupRequestStatus.REJECTED);
        assertThat(auditLogRepository.findAll())
                .anyMatch(a -> a.getAction() == AuditAction.SIGNUP_REQUEST_REJECTED);

        ResponseEntity<String> approveAfterReject = restTemplate.exchange(
                "/api/v1/platform/signup-requests/" + signupRequest.getId() + "/approve",
                HttpMethod.POST, new HttpEntity<>(new ProvisionApproveRequest(null, true), headers), String.class);
        assertThat(approveAfterReject.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void schoolStatusChangeMirrorsOntoSubscriptionAndIsAudited() {
        HttpHeaders headers = platformHeaders();
        var defaultSchool = schoolRepository.findBySlug("default-school").orElseThrow();

        SchoolStatusUpdateRequest request = new SchoolStatusUpdateRequest(SchoolStatus.PAST_DUE);
        ResponseEntity<SchoolAdminDto> response = restTemplate.exchange(
                "/api/v1/platform/schools/" + defaultSchool.getId() + "/status",
                HttpMethod.PATCH, new HttpEntity<>(request, headers), SchoolAdminDto.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo(SchoolStatus.PAST_DUE);

        assertThat(subscriptionRepository.findBySchoolId(defaultSchool.getId()).orElseThrow().getStatus())
                .isEqualTo(SchoolStatus.PAST_DUE);
        assertThat(auditLogRepository.findAll())
                .anyMatch(a -> a.getAction() == AuditAction.SCHOOL_STATUS_CHANGED && defaultSchool.getId().equals(a.getTargetSchoolId()));

        // Restore so other tests sharing this school aren't affected.
        restTemplate.exchange(
                "/api/v1/platform/schools/" + defaultSchool.getId() + "/status",
                HttpMethod.PATCH, new HttpEntity<>(new SchoolStatusUpdateRequest(SchoolStatus.ACTIVE), platformHeaders()), SchoolAdminDto.class);
    }

    @Test
    void mfaEnrollmentRequiresConfirmationBeforeItGatesLogin() {
        HttpHeaders headers = platformHeaders();

        ResponseEntity<MfaEnrollResponse> enroll = restTemplate.exchange(
                "/api/v1/platform/auth/mfa/enroll", HttpMethod.POST, new HttpEntity<>(headers), MfaEnrollResponse.class);
        assertThat(enroll.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(enroll.getBody().secret()).isNotBlank();

        // Confirming with a bogus code doesn't enroll MFA — login still works without a code.
        MfaConfirmRequest badConfirm = new MfaConfirmRequest(enroll.getBody().secret(), "000000");
        ResponseEntity<String> confirmResponse = restTemplate.exchange(
                "/api/v1/platform/auth/mfa/confirm", HttpMethod.POST, new HttpEntity<>(badConfirm, headers), String.class);
        assertThat(confirmResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        PlatformLoginRequest stillNoMfaNeeded = new PlatformLoginRequest(PLATFORM_EMAIL, PLATFORM_PASSWORD, null);
        ResponseEntity<PlatformAuthResponse> loginResponse = restTemplate.postForEntity(
                "/api/v1/platform/auth/login", stillNoMfaNeeded, PlatformAuthResponse.class);
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginResponse.getBody().mfaEnrolled()).isFalse();
    }

    @Test
    void confirmingEnrollmentWithAValidCodeThenGatesLoginBehindMfa() throws Exception {
        HttpHeaders headers = platformHeaders();
        try {
            ResponseEntity<MfaEnrollResponse> enroll = restTemplate.exchange(
                    "/api/v1/platform/auth/mfa/enroll", HttpMethod.POST, new HttpEntity<>(headers), MfaEnrollResponse.class);
            String secret = enroll.getBody().secret();
            String validCode = new DefaultCodeGenerator().generate(secret, currentTotpTimeWindow());

            ResponseEntity<Void> confirm = restTemplate.exchange(
                    "/api/v1/platform/auth/mfa/confirm", HttpMethod.POST,
                    new HttpEntity<>(new MfaConfirmRequest(secret, validCode), headers), Void.class);
            assertThat(confirm.getStatusCode()).isEqualTo(HttpStatus.OK);

            // Now enrolled: logging in without a code is rejected...
            PlatformLoginRequest noCode = new PlatformLoginRequest(PLATFORM_EMAIL, PLATFORM_PASSWORD, null);
            ResponseEntity<String> rejected = restTemplate.postForEntity("/api/v1/platform/auth/login", noCode, String.class);
            assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

            // ...but succeeds with a freshly-generated valid code.
            String loginCode = new DefaultCodeGenerator().generate(secret, currentTotpTimeWindow());
            PlatformLoginRequest withCode = new PlatformLoginRequest(PLATFORM_EMAIL, PLATFORM_PASSWORD, loginCode);
            ResponseEntity<PlatformAuthResponse> accepted = restTemplate.postForEntity(
                    "/api/v1/platform/auth/login", withCode, PlatformAuthResponse.class);
            assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(accepted.getBody().mfaEnrolled()).isTrue();
        } finally {
            // Other tests in this class assume the seeded operator has no MFA enrolled.
            PlatformUser operator = platformUserRepository.findByEmail(PLATFORM_EMAIL).orElseThrow();
            operator.setMfaSecret(null);
            platformUserRepository.save(operator);
        }
    }

    /** RFC 6238's counter — matches {@code DefaultCodeVerifier}'s default 30-second time step. */
    private long currentTotpTimeWindow() {
        return Instant.now().getEpochSecond() / 30;
    }
}
