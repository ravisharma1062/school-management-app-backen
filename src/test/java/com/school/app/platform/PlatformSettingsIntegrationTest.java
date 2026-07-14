package com.school.app.platform;

import com.school.app.common.AbstractIntegrationTest;
import com.school.app.school.School;
import com.school.app.school.SchoolRepository;
import com.school.app.school.SchoolStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers Phase MT-6f's Definition of Done: self-service provisioning (when an operator opts in)
 * reuses the audited MT-3 path (ProvisioningService.approve) and stays isolated — proven here by
 * asserting the auto-provisioned school passes the same checks {@code OperatorProvisioningIntegrationTest}
 * already covers for the operator-driven approval, not by re-deriving them.
 */
class PlatformSettingsIntegrationTest extends AbstractIntegrationTest {

    private static final String PLATFORM_EMAIL = "operator@school.app";
    private static final String PLATFORM_PASSWORD = "Operator@123";

    @Autowired
    private PlatformSettingsRepository platformSettingsRepository;
    @Autowired
    private SignupRequestRepository signupRequestRepository;
    @Autowired
    private SchoolRepository schoolRepository;
    @Autowired
    private SubscriptionRepository subscriptionRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;

    @AfterEach
    void resetAutoApprove() {
        PlatformSettings settings = platformSettingsRepository.findById(PlatformSettings.SINGLETON_ID).orElseThrow();
        settings.setAutoApproveSignups(false);
        platformSettingsRepository.save(settings);
    }

    private HttpHeaders platformHeaders() {
        PlatformLoginRequest request = new PlatformLoginRequest(PLATFORM_EMAIL, PLATFORM_PASSWORD, null);
        ResponseEntity<PlatformAuthResponse> response = restTemplate.postForEntity(
                "/api/v1/platform/auth/login", request, PlatformAuthResponse.class);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(response.getBody().accessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @Test
    void defaultsToAutoApproveDisabled() {
        ResponseEntity<PlatformSettingsDto> response = restTemplate.exchange(
                "/api/v1/platform/settings", HttpMethod.GET, new HttpEntity<>(platformHeaders()), PlatformSettingsDto.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().autoApproveSignups()).isFalse();
    }

    @Test
    void tenantTokenCannotReadOrChangeSettings() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // No auth at all — a bare unauthenticated call must already be rejected.
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/platform/settings", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void enablingAutoApproveMakesNewSignupsProvisionInstantlyAndAuditedWithAnActor() {
        ResponseEntity<PlatformSettingsDto> updateResponse = restTemplate.exchange(
                "/api/v1/platform/settings", HttpMethod.PATCH,
                new HttpEntity<>(new PlatformSettingsUpdateRequest(true, null), platformHeaders()), PlatformSettingsDto.class);
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody().autoApproveSignups()).isTrue();
        assertThat(auditLogRepository.findAll())
                .anyMatch(a -> a.getAction() == AuditAction.PLATFORM_SETTINGS_UPDATED && a.getActor() != null);

        String email = "auto-" + UUID.randomUUID() + "@sunrise.example";
        PublicSignupRequest signup = new PublicSignupRequest(
                "Auto Provisioned School", "Auto Contact", email, "+911234567890",
                PlanCode.STANDARD, true, false, "any-token-since-captcha-is-unconfigured");
        ResponseEntity<Void> signupResponse = restTemplate.postForEntity("/api/v1/public/signup-requests", signup, Void.class);
        assertThat(signupResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        assertThat(signupRequestRepository.findByContactEmailAndStatus(email, SignupRequestStatus.APPROVED)).isPresent();
        School school = schoolRepository.findAll().stream()
                .filter(s -> s.getName().equals("Auto Provisioned School"))
                .findFirst().orElseThrow();
        assertThat(school.getStatus()).isEqualTo(SchoolStatus.ACTIVE);
        assertThat(subscriptionRepository.findBySchoolId(school.getId()).orElseThrow().getPlan().getCode())
                .isEqualTo(PlanCode.STANDARD);

        assertThat(auditLogRepository.findAll())
                .filteredOn(a -> a.getAction() == AuditAction.SIGNUP_REQUEST_APPROVED && school.getId().equals(a.getTargetSchoolId()))
                .singleElement()
                .satisfies(a -> assertThat(a.getActor()).isNull());
    }

    @Test
    void withAutoApproveDisabledNewSignupsStayPendingReview() {
        String email = "pending-" + UUID.randomUUID() + "@sunrise.example";
        PublicSignupRequest signup = new PublicSignupRequest(
                "Pending Review School", "Pending Contact", email, "+911234567890",
                PlanCode.STANDARD, true, false, "any-token-since-captcha-is-unconfigured");
        ResponseEntity<Void> signupResponse = restTemplate.postForEntity("/api/v1/public/signup-requests", signup, Void.class);
        assertThat(signupResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        assertThat(signupRequestRepository.findByContactEmailAndStatus(email, SignupRequestStatus.NEW)).isPresent();
    }
}
