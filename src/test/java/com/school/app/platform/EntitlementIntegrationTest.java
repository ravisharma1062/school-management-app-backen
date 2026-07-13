package com.school.app.platform;

import com.school.app.auth.AuthResponse;
import com.school.app.auth.LoginRequest;
import com.school.app.common.AbstractIntegrationTest;
import com.school.app.common.notification.NotificationEventType;
import com.school.app.common.notification.NotificationLog;
import com.school.app.common.notification.NotificationLogRepository;
import com.school.app.common.notification.NotificationPreference;
import com.school.app.common.notification.NotificationPreferenceRepository;
import com.school.app.common.notification.NotificationServiceImpl;
import com.school.app.common.security.TenantContext;
import com.school.app.school.School;
import com.school.app.school.SchoolRepository;
import com.school.app.school.SchoolStatus;
import com.school.app.student.Student;
import com.school.app.student.StudentCreateRequest;
import com.school.app.student.StudentRepository;
import com.school.app.user.Role;
import com.school.app.user.User;
import com.school.app.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers Phase MT-2's Definition of Done: entitlement gating per feature, MAX_STUDENTS limit
 * enforcement, the subscription endpoint, and cross-tenant isolation of entitlements.
 *
 * <p>Builds a school with every entitlement disabled except a 2-student MAX_STUDENTS cap, so each
 * test can flip exactly the one entitlement it cares about rather than fighting the "everything
 * enabled" backfill the default school got from V18.
 */
class EntitlementIntegrationTest extends AbstractIntegrationTest {

    private static final String TEST_PASSWORD = "Password@123";

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
    private StudentRepository studentRepository;
    @Autowired
    private NotificationPreferenceRepository notificationPreferenceRepository;
    @Autowired
    private NotificationLogRepository notificationLogRepository;
    @Autowired
    private NotificationServiceImpl notificationServiceImpl;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private School school;
    private Subscription subscription;
    private String adminToken;

    @BeforeEach
    void seedSchoolWithControllableEntitlements() {
        school = schoolRepository.save(School.builder()
                .name("Entitlement Test School")
                .slug("ent-test-" + UUID.randomUUID())
                .status(SchoolStatus.ACTIVE)
                .build());
        TenantContext.set(school.getId());

        SubscriptionPlan basicPlan = subscriptionPlanRepository.findByCode(PlanCode.BASIC).orElseThrow();
        subscription = subscriptionRepository.save(Subscription.builder()
                .school(school)
                .plan(basicPlan)
                .status(SchoolStatus.ACTIVE)
                .build());

        for (FeatureKey key : FeatureKey.values()) {
            entitlementRepository.save(Entitlement.builder()
                    .subscription(subscription)
                    .featureKey(key)
                    .enabled(key == FeatureKey.MAX_STUDENTS)
                    .limitValue(key == FeatureKey.MAX_STUDENTS ? 2 : null)
                    .build());
        }

        String adminEmail = "ent-admin-" + UUID.randomUUID() + "@school.app";
        userRepository.save(User.builder()
                .name("Ent Admin")
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

    /** The Flyway-seeded default admin's password is fixed ("Admin@123"), unlike {@link #login}'s TEST_PASSWORD. */
    private String loginAsDefaultSchoolAdmin() {
        AuthResponse response = restTemplate.postForObject(
                "/api/v1/auth/login", new LoginRequest("admin@school.app", "Admin@123"), AuthResponse.class);
        return response.accessToken();
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @Test
    void requiresEntitlementBlocksNonEntitledFeatureWith403() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/analytics/attendance", HttpMethod.GET, new HttpEntity<>(headers()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("FEATURE_NOT_ENTITLED");
    }

    @Test
    void enablingTheEntitlementLetsTheSameEndpointThrough() {
        Entitlement analytics = entitlementRepository.findBySubscriptionIdAndFeatureKey(subscription.getId(), FeatureKey.ANALYTICS)
                .orElseThrow();
        analytics.setEnabled(true);
        entitlementRepository.save(analytics);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/analytics/attendance", HttpMethod.GET, new HttpEntity<>(headers()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void maxStudentsLimitBlocksCreateAtTheBoundaryWith409() {
        ResponseEntity<String> first = createStudent("R1");
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> second = createStudent("R2");
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> third = createStudent("R3");
        assertThat(third.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(third.getBody()).contains("LIMIT_EXCEEDED");
    }

    private ResponseEntity<String> createStudent(String rollNo) {
        StudentCreateRequest request = new StudentCreateRequest(
                "Student " + rollNo, rollNo, "5", "A", LocalDate.of(2014, 1, 1), null);
        return restTemplate.exchange(
                "/api/v1/students", HttpMethod.POST, new HttpEntity<>(request, headers()), String.class);
    }

    @Test
    void subscriptionEndpointReturnsThisSchoolsPlanAndEntitlementsOnly() {
        ResponseEntity<SubscriptionDto> response = restTemplate.exchange(
                "/api/v1/subscription", HttpMethod.GET, new HttpEntity<>(headers()), SubscriptionDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        SubscriptionDto dto = response.getBody();
        assertThat(dto).isNotNull();
        assertThat(dto.planCode()).isEqualTo(PlanCode.BASIC);
        assertThat(dto.status()).isEqualTo(SchoolStatus.ACTIVE);
        assertThat(dto.entitlements()).hasSize(FeatureKey.values().length);
        assertThat(dto.entitlements())
                .filteredOn(e -> e.featureKey() == FeatureKey.MAX_STUDENTS)
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.enabled()).isTrue();
                    assertThat(e.limitValue()).isEqualTo(2);
                });
        assertThat(dto.entitlements())
                .filteredOn(e -> e.featureKey() == FeatureKey.ANALYTICS)
                .singleElement()
                .satisfies(e -> assertThat(e.enabled()).isFalse());
    }

    @Test
    void oneSchoolsDisabledEntitlementNeverAffectsAnotherSchool() {
        // This school has ANALYTICS disabled; the pre-existing default school was backfilled with
        // everything enabled in V18. Prove they don't interfere with each other either way.
        ResponseEntity<String> thisSchool = restTemplate.exchange(
                "/api/v1/analytics/attendance", HttpMethod.GET, new HttpEntity<>(headers()), String.class);
        assertThat(thisSchool.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        HttpHeaders defaultAdminHeaders = new HttpHeaders();
        defaultAdminHeaders.setBearerAuth(loginAsDefaultSchoolAdmin());
        ResponseEntity<String> defaultSchoolResponse = restTemplate.exchange(
                "/api/v1/analytics/attendance", HttpMethod.GET, new HttpEntity<>(defaultAdminHeaders), String.class);
        assertThat(defaultSchoolResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void disablingSmsEntitlementSkipsSendAndLogsSkippedNotEntitled_thenReenablingResumes() {
        TenantContext.set(school.getId());
        try {
            notificationPreferenceRepository.save(NotificationPreference.builder()
                    .eventType(NotificationEventType.ATTENDANCE_ABSENT)
                    .smsEnabled(true)
                    .emailEnabled(false)
                    .build());
            User recipient = userRepository.save(User.builder()
                    .name("Recipient")
                    .email("recipient-" + UUID.randomUUID() + "@school.app")
                    .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                    .phone("+911234567890")
                    .role(Role.PARENT)
                    .build());

            notificationServiceImpl.notify(NotificationEventType.ATTENDANCE_ABSENT, recipient, "first", "msg");

            List<NotificationLog> logsAfterDisabled = notificationLogRepository.findAll();
            assertThat(logsAfterDisabled)
                    .filteredOn(l -> "first".equals(l.getSubject()))
                    .singleElement()
                    .satisfies(l -> assertThat(l.getStatus()).isEqualTo(com.school.app.common.notification.NotificationStatus.SKIPPED_NOT_ENTITLED));

            Entitlement smsEntitlement = entitlementRepository
                    .findBySubscriptionIdAndFeatureKey(subscription.getId(), FeatureKey.SMS_NOTIFICATIONS)
                    .orElseThrow();
            smsEntitlement.setEnabled(true);
            entitlementRepository.save(smsEntitlement);

            notificationServiceImpl.notify(NotificationEventType.ATTENDANCE_ABSENT, recipient, "second", "msg");

            List<NotificationLog> logsAfterReenabled = notificationLogRepository.findAll();
            assertThat(logsAfterReenabled)
                    .filteredOn(l -> "second".equals(l.getSubject()))
                    .singleElement()
                    .satisfies(l -> assertThat(l.getStatus())
                            .isNotEqualTo(com.school.app.common.notification.NotificationStatus.SKIPPED_NOT_ENTITLED));
        } finally {
            School defaultSchool = schoolRepository.findBySlug("default-school").orElseThrow();
            TenantContext.set(defaultSchool.getId());
        }
    }
}
