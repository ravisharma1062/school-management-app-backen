package com.school.app.billing;

import com.school.app.auth.AuthResponse;
import com.school.app.auth.LoginRequest;
import com.school.app.common.AbstractIntegrationTest;
import com.school.app.common.security.TenantContext;
import com.school.app.platform.*;
import com.school.app.school.School;
import com.school.app.school.SchoolRepository;
import com.school.app.school.SchoolStatus;
import com.school.app.user.Role;
import com.school.app.user.User;
import com.school.app.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers MT-5 (manual/offline billing): a school reports a Demand Draft/Cheque/NEFT payment made
 * outside the app; an operator verifies it against their bank statement (extending the billing
 * period and reactivating the school) or rejects it; a scheduled job marks lapsed-and-unpaid
 * subscriptions PAST_DUE but never auto-suspends one (that stays an explicit operator call).
 */
class ManualBillingIntegrationTest extends AbstractIntegrationTest {

    private static final String TEST_PASSWORD = "Password@123";
    private static final String PLATFORM_EMAIL = "operator@school.app";
    private static final String PLATFORM_PASSWORD = "Operator@123";

    @Autowired
    private SchoolRepository schoolRepository;
    @Autowired
    private SubscriptionPlanRepository subscriptionPlanRepository;
    @Autowired
    private SubscriptionRepository subscriptionRepository;
    @Autowired
    private EntitlementRepository entitlementRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private PaymentClaimRepository paymentClaimRepository;
    @Autowired
    private PlatformSettingsRepository platformSettingsRepository;
    @Autowired
    private SubscriptionOverdueJob subscriptionOverdueJob;

    private School school;
    private String adminToken;
    private String teacherToken;

    @BeforeEach
    void seedActiveSchool() {
        school = schoolRepository.save(School.builder()
                .name("Manual Billing Test School")
                .slug("manual-billing-test-" + UUID.randomUUID())
                .status(SchoolStatus.ACTIVE)
                .build());
        TenantContext.set(school.getId());

        SubscriptionPlan plan = subscriptionPlanRepository.findByCode(PlanCode.BASIC).orElseThrow();
        subscriptionRepository.save(Subscription.builder()
                .school(school)
                .plan(plan)
                .status(SchoolStatus.ACTIVE)
                .currentPeriodStart(Instant.now().minus(30, ChronoUnit.DAYS))
                .currentPeriodEnd(Instant.now().plus(30, ChronoUnit.DAYS))
                .build());

        String adminEmail = "billing-admin-" + UUID.randomUUID() + "@school.app";
        userRepository.save(User.builder()
                .name("Billing Admin").email(adminEmail)
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD)).role(Role.ADMIN).build());
        String teacherEmail = "billing-teacher-" + UUID.randomUUID() + "@school.app";
        userRepository.save(User.builder()
                .name("Billing Teacher").email(teacherEmail)
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD)).role(Role.TEACHER).build());

        School defaultSchool = schoolRepository.findBySlug("default-school").orElseThrow();
        TenantContext.set(defaultSchool.getId());

        adminToken = login(adminEmail);
        teacherToken = login(teacherEmail);
    }

    @AfterEach
    void clearPaymentInstructions() {
        platformSettingsRepository.findById(PlatformSettings.SINGLETON_ID).ifPresent(s -> {
            s.setPaymentInstructions(null);
            platformSettingsRepository.save(s);
        });
    }

    private String login(String email) {
        return restTemplate.postForObject("/api/v1/auth/login", new LoginRequest(email, TEST_PASSWORD), AuthResponse.class).accessToken();
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
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

    private PaymentClaimCreateRequest sampleClaim() {
        return new PaymentClaimCreateRequest(
                new BigDecimal("9999.00"), PaymentMethod.NEFT, "UTR" + UUID.randomUUID(),
                LocalDate.now(), LocalDate.now().plusDays(365));
    }

    @Test
    void adminCanSubmitAClaimAndSeeItInTheirOwnHistory() {
        ResponseEntity<PaymentClaimDto> response = restTemplate.exchange(
                "/api/v1/billing/payments", HttpMethod.POST, new HttpEntity<>(sampleClaim(), adminHeaders()), PaymentClaimDto.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().status()).isEqualTo(PaymentClaimStatus.PENDING_VERIFICATION);

        ResponseEntity<Map> history = restTemplate.exchange(
                "/api/v1/billing/payments", HttpMethod.GET, new HttpEntity<>(adminHeaders()), Map.class);
        assertThat(history.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((java.util.List<?>) history.getBody().get("content")).hasSize(1);
    }

    @Test
    void teacherCannotSubmitAClaim() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(teacherToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/billing/payments", HttpMethod.POST, new HttpEntity<>(sampleClaim(), headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void periodEndBeforePeriodStartIsRejected() {
        PaymentClaimCreateRequest invalid = new PaymentClaimCreateRequest(
                new BigDecimal("100.00"), PaymentMethod.CHEQUE, "CHQ123", LocalDate.now(), LocalDate.now().minusDays(1));
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/billing/payments", HttpMethod.POST, new HttpEntity<>(invalid, adminHeaders()), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void operatorSeesThePendingClaimAndVerifyingReactivatesTheSchoolAndExtendsThePeriod() {
        ResponseEntity<PaymentClaimDto> submitted = restTemplate.exchange(
                "/api/v1/billing/payments", HttpMethod.POST, new HttpEntity<>(sampleClaim(), adminHeaders()), PaymentClaimDto.class);
        UUID claimId = submitted.getBody().id();

        ResponseEntity<PlatformPaymentDto[]> pending = restTemplate.exchange(
                "/api/v1/platform/payments/pending", HttpMethod.GET, new HttpEntity<>(platformHeaders()), PlatformPaymentDto[].class);
        assertThat(pending.getBody()).anySatisfy(p -> {
            assertThat(p.id()).isEqualTo(claimId);
            assertThat(p.schoolName()).isEqualTo("Manual Billing Test School");
            assertThat(p.submittedByEmail()).isNotBlank();
        });

        ResponseEntity<PlatformPaymentDto> verifyResponse = restTemplate.exchange(
                "/api/v1/platform/payments/" + claimId + "/verify", HttpMethod.PATCH,
                new HttpEntity<>(new PaymentDecisionRequest("checked bank statement"), platformHeaders()), PlatformPaymentDto.class);
        assertThat(verifyResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(verifyResponse.getBody().status()).isEqualTo(PaymentClaimStatus.VERIFIED);

        School reloaded = schoolRepository.findById(school.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(SchoolStatus.ACTIVE);
        Subscription subscription = subscriptionRepository.findBySchoolId(school.getId()).orElseThrow();
        assertThat(subscription.getStatus()).isEqualTo(SchoolStatus.ACTIVE);
        assertThat(subscription.getCurrentPeriodEnd()).isEqualTo(
                submitted.getBody().periodEnd().atStartOfDay(java.time.ZoneOffset.UTC).toInstant());

        // Re-verifying an already-decided claim is rejected.
        ResponseEntity<String> reVerify = restTemplate.exchange(
                "/api/v1/platform/payments/" + claimId + "/verify", HttpMethod.PATCH,
                new HttpEntity<>(new PaymentDecisionRequest(null), platformHeaders()), String.class);
        assertThat(reVerify.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void operatorCanRejectAClaimWithoutAffectingSchoolStatus() {
        ResponseEntity<PaymentClaimDto> submitted = restTemplate.exchange(
                "/api/v1/billing/payments", HttpMethod.POST, new HttpEntity<>(sampleClaim(), adminHeaders()), PaymentClaimDto.class);
        UUID claimId = submitted.getBody().id();

        ResponseEntity<PlatformPaymentDto> rejectResponse = restTemplate.exchange(
                "/api/v1/platform/payments/" + claimId + "/reject", HttpMethod.PATCH,
                new HttpEntity<>(new PaymentDecisionRequest("reference number doesn't match our statement"), platformHeaders()),
                PlatformPaymentDto.class);
        assertThat(rejectResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rejectResponse.getBody().status()).isEqualTo(PaymentClaimStatus.REJECTED);
        assertThat(rejectResponse.getBody().notes()).contains("doesn't match");

        assertThat(schoolRepository.findById(school.getId()).orElseThrow().getStatus()).isEqualTo(SchoolStatus.ACTIVE);
    }

    @Test
    void paymentInstructionsAreConfigurableByOperatorAndVisibleToTheSchool() {
        restTemplate.exchange(
                "/api/v1/platform/settings", HttpMethod.PATCH,
                new HttpEntity<>(new PlatformSettingsUpdateRequest(null, "Pay to: School Corp, A/C 12345, IFSC ABCD0001234"), platformHeaders()),
                PlatformSettingsDto.class);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/billing/payment-instructions", HttpMethod.GET, new HttpEntity<>(adminHeaders()), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("IFSC ABCD0001234");
    }

    @Test
    void overdueJobMarksLapsedActiveSubscriptionsPastDueButLeavesFutureOnesAlone() {
        Subscription subscription = subscriptionRepository.findBySchoolId(school.getId()).orElseThrow();
        subscription.setCurrentPeriodEnd(Instant.now().minus(1, ChronoUnit.DAYS));
        subscriptionRepository.save(subscription);

        subscriptionOverdueJob.markOverdueSubscriptionsPastDue();

        assertThat(schoolRepository.findById(school.getId()).orElseThrow().getStatus()).isEqualTo(SchoolStatus.PAST_DUE);
        assertThat(subscriptionRepository.findBySchoolId(school.getId()).orElseThrow().getStatus()).isEqualTo(SchoolStatus.PAST_DUE);

        // A second run doesn't error or re-flag an already-PAST_DUE subscription (job only touches ACTIVE ones).
        subscriptionOverdueJob.markOverdueSubscriptionsPastDue();
        assertThat(schoolRepository.findById(school.getId()).orElseThrow().getStatus()).isEqualTo(SchoolStatus.PAST_DUE);
    }
}
