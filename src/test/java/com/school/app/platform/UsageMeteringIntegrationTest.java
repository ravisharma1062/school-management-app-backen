package com.school.app.platform;

import com.school.app.auth.AuthResponse;
import com.school.app.auth.LoginRequest;
import com.school.app.common.AbstractIntegrationTest;
import com.school.app.common.notification.NotificationChannel;
import com.school.app.common.notification.NotificationEventType;
import com.school.app.common.notification.NotificationLog;
import com.school.app.common.notification.NotificationLogRepository;
import com.school.app.common.notification.NotificationStatus;
import com.school.app.common.security.TenantContext;
import com.school.app.school.School;
import com.school.app.school.SchoolRepository;
import com.school.app.school.SchoolStatus;
import com.school.app.student.StudentCreateRequest;
import com.school.app.user.Role;
import com.school.app.user.User;
import com.school.app.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers MT-6c's Definition of Done: tenant self-service usage-vs-limit visibility on
 * {@code GET /subscription}, and platform cross-tenant usage visibility via the new per-school
 * usage endpoint, the subscription-admin view, and the aggregate analytics totals.
 */
class UsageMeteringIntegrationTest extends AbstractIntegrationTest {

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
    private NotificationLogRepository notificationLogRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private School school;
    private String adminToken;

    @BeforeEach
    void seedSchoolWithAStudentQuota() {
        school = schoolRepository.save(School.builder()
                .name("Usage Test School")
                .slug("usage-test-" + UUID.randomUUID())
                .status(SchoolStatus.ACTIVE)
                .build());
        TenantContext.set(school.getId());

        SubscriptionPlan basicPlan = subscriptionPlanRepository.findByCode(PlanCode.BASIC).orElseThrow();
        Subscription subscription = subscriptionRepository.save(Subscription.builder()
                .school(school)
                .plan(basicPlan)
                .status(SchoolStatus.ACTIVE)
                .build());

        for (FeatureKey key : FeatureKey.values()) {
            entitlementRepository.save(Entitlement.builder()
                    .subscription(subscription)
                    .featureKey(key)
                    .enabled(key == FeatureKey.MAX_STUDENTS)
                    .limitValue(key == FeatureKey.MAX_STUDENTS ? 5 : null)
                    .build());
        }

        String adminEmail = "usage-admin-" + UUID.randomUUID() + "@school.app";
        userRepository.save(User.builder()
                .name("Usage Admin")
                .email(adminEmail)
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .role(Role.ADMIN)
                .build());

        School defaultSchool = schoolRepository.findBySlug("default-school").orElseThrow();
        TenantContext.set(defaultSchool.getId());

        adminToken = login(adminEmail);
    }

    private String login(String email) {
        LoginRequest request = new LoginRequest(email, TEST_PASSWORD);
        AuthResponse response = restTemplate.postForObject("/api/v1/auth/login", request, AuthResponse.class);
        return response.accessToken();
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

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

    private void createStudent(String rollNo) {
        StudentCreateRequest request = new StudentCreateRequest(
                "Student " + rollNo, rollNo, "5", "A", LocalDate.of(2014, 1, 1), null);
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/students", HttpMethod.POST, new HttpEntity<>(request, headers()), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private void logSentNotification(NotificationChannel channel) {
        TenantContext.set(school.getId());
        notificationLogRepository.save(NotificationLog.builder()
                .eventType(NotificationEventType.ATTENDANCE_ABSENT)
                .channel(channel)
                .recipient("someone@example.com")
                .status(NotificationStatus.SENT)
                .build());
        School defaultSchool = schoolRepository.findBySlug("default-school").orElseThrow();
        TenantContext.set(defaultSchool.getId());
    }

    @Test
    void tenantSubscriptionEndpointReportsCurrentUsageOnlyForTheQuotaFeature() {
        createStudent("R1");
        createStudent("R2");

        ResponseEntity<SubscriptionDto> response = restTemplate.exchange(
                "/api/v1/subscription", HttpMethod.GET, new HttpEntity<>(headers()), SubscriptionDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().entitlements())
                .filteredOn(e -> e.featureKey() == FeatureKey.MAX_STUDENTS)
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.limitValue()).isEqualTo(5);
                    assertThat(e.currentUsage()).isEqualTo(2L);
                });
        assertThat(response.getBody().entitlements())
                .filteredOn(e -> e.featureKey() != FeatureKey.MAX_STUDENTS)
                .allSatisfy(e -> assertThat(e.currentUsage()).isNull());
    }

    @Test
    void platformPerSchoolUsageEndpointReportsStudentsAndNotificationsSentThisMonth() {
        createStudent("R1");
        createStudent("R2");
        createStudent("R3");
        logSentNotification(NotificationChannel.EMAIL);
        logSentNotification(NotificationChannel.EMAIL);
        logSentNotification(NotificationChannel.SMS);

        ResponseEntity<SchoolUsageDto> response = restTemplate.exchange(
                "/api/v1/platform/schools/" + school.getId() + "/usage",
                HttpMethod.GET, new HttpEntity<>(platformHeaders()), SchoolUsageDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        SchoolUsageDto dto = response.getBody();
        assertThat(dto).isNotNull();
        assertThat(dto.schoolId()).isEqualTo(school.getId());
        assertThat(dto.activeStudentCount()).isEqualTo(3L);
        assertThat(dto.maxStudentsLimit()).isEqualTo(5);
        assertThat(dto.emailsSentThisMonth()).isEqualTo(2L);
        assertThat(dto.smsSentThisMonth()).isEqualTo(1L);
    }

    @Test
    void platformSubscriptionAdminViewAlsoIncludesCurrentUsage() {
        createStudent("R1");

        ResponseEntity<SubscriptionAdminDto> response = restTemplate.exchange(
                "/api/v1/platform/subscriptions/" + school.getId(),
                HttpMethod.GET, new HttpEntity<>(platformHeaders()), SubscriptionAdminDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().entitlements())
                .filteredOn(e -> e.featureKey() == FeatureKey.MAX_STUDENTS)
                .singleElement()
                .satisfies(e -> assertThat(e.currentUsage()).isEqualTo(1L));
    }

    @Test
    void platformAnalyticsAggregateTotalsIncludeThisSchoolsUsage() {
        HttpHeaders headers = platformHeaders();
        PlatformAnalyticsDto before = restTemplate.exchange(
                        "/api/v1/platform/analytics", HttpMethod.GET, new HttpEntity<>(headers), PlatformAnalyticsDto.class)
                .getBody();

        createStudent("R1");
        createStudent("R2");
        logSentNotification(NotificationChannel.EMAIL);
        logSentNotification(NotificationChannel.SMS);

        PlatformAnalyticsDto after = restTemplate.exchange(
                        "/api/v1/platform/analytics", HttpMethod.GET, new HttpEntity<>(platformHeaders()), PlatformAnalyticsDto.class)
                .getBody();

        assertThat(after.totalActiveStudents()).isEqualTo(before.totalActiveStudents() + 2);
        assertThat(after.totalEmailsSentThisMonth()).isEqualTo(before.totalEmailsSentThisMonth() + 1);
        assertThat(after.totalSmsSentThisMonth()).isEqualTo(before.totalSmsSentThisMonth() + 1);
    }
}
