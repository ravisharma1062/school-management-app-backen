package com.school.app.export;

import com.school.app.auth.AuthResponse;
import com.school.app.auth.LoginRequest;
import com.school.app.common.AbstractIntegrationTest;
import com.school.app.common.security.TenantContext;
import com.school.app.notice.Notice;
import com.school.app.notice.NoticeRepository;
import com.school.app.notice.TargetRole;
import com.school.app.platform.Entitlement;
import com.school.app.platform.EntitlementRepository;
import com.school.app.platform.FeatureKey;
import com.school.app.platform.PlanCode;
import com.school.app.platform.Subscription;
import com.school.app.platform.SubscriptionPlan;
import com.school.app.platform.SubscriptionPlanRepository;
import com.school.app.platform.SubscriptionRepository;
import com.school.app.school.School;
import com.school.app.school.SchoolRepository;
import com.school.app.school.SchoolStatus;
import com.school.app.student.Student;
import com.school.app.student.StudentRepository;
import com.school.app.user.Role;
import com.school.app.user.User;
import com.school.app.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers Phase MT-6d's buildable Definition of Done: an admin can export their school's own data.
 * (The legally-reviewed retention/deletion policy for cancelled tenants is intentionally not
 * implemented — see {@link DataExportService}'s Javadoc.)
 */
class DataExportIntegrationTest extends AbstractIntegrationTest {

    private static final String TEST_PASSWORD = "Password@123";
    private static final Set<String> EXPECTED_ENTRIES = Set.of(
            "students.csv", "users.csv", "teachers.csv", "attendance.csv", "homework.csv",
            "homework_submissions.csv", "fees.csv", "payments.csv", "exam_results.csv",
            "leave_requests.csv", "books.csv", "book_issues.csv", "notices.csv", "events.csv",
            "event_rsvps.csv", "timetable.csv", "conversations.csv", "messages.csv");

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
    private NoticeRepository noticeRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private School school;
    private String adminToken;
    private String teacherToken;

    @BeforeEach
    void seedSchoolWithSomeData() {
        school = schoolRepository.save(School.builder()
                .name("Export Test School")
                .slug("export-test-" + UUID.randomUUID())
                .status(SchoolStatus.ACTIVE)
                .build());
        TenantContext.set(school.getId());

        SubscriptionPlan plan = subscriptionPlanRepository.findByCode(PlanCode.PREMIUM).orElseThrow();
        Subscription subscription = subscriptionRepository.save(Subscription.builder()
                .school(school).plan(plan).status(SchoolStatus.ACTIVE).build());
        for (FeatureKey key : FeatureKey.values()) {
            entitlementRepository.save(Entitlement.builder()
                    .subscription(subscription).featureKey(key).enabled(true).limitValue(null).build());
        }

        String adminEmail = "export-admin-" + UUID.randomUUID() + "@school.app";
        userRepository.save(User.builder()
                .name("Export Admin").email(adminEmail)
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD)).role(Role.ADMIN).build());
        String teacherEmail = "export-teacher-" + UUID.randomUUID() + "@school.app";
        userRepository.save(User.builder()
                .name("Export Teacher").email(teacherEmail)
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD)).role(Role.TEACHER).build());

        studentRepository.save(Student.builder()
                .name("Riya Sharma").rollNo("R1").studentClass("5").section("A")
                .dob(LocalDate.of(2014, 3, 20)).active(true).build());
        noticeRepository.save(Notice.builder()
                .title("Independence Day").description("School closed").targetRole(TargetRole.ALL).active(true).build());

        School defaultSchool = schoolRepository.findBySlug("default-school").orElseThrow();
        TenantContext.set(defaultSchool.getId());

        adminToken = login(adminEmail);
        teacherToken = login(teacherEmail);
    }

    private String login(String email) {
        AuthResponse response = restTemplate.postForObject(
                "/api/v1/auth/login", new LoginRequest(email, TEST_PASSWORD), AuthResponse.class);
        return response.accessToken();
    }

    @Test
    void adminCanExportTheSchoolsOwnDataAsAZipOfCsvs() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        ResponseEntity<byte[]> response = restTemplate.exchange(
                "/api/v1/data-export", HttpMethod.GET, new HttpEntity<>(headers), byte[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains("attachment");
        assertThat(response.getBody()).isNotNull();

        Set<String> entries = new HashSet<>();
        StringBuilder studentsCsv = new StringBuilder();
        StringBuilder noticesCsv = new StringBuilder();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(response.getBody()))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.add(entry.getName());
                String content = new String(zip.readAllBytes());
                if (entry.getName().equals("students.csv")) studentsCsv.append(content);
                if (entry.getName().equals("notices.csv")) noticesCsv.append(content);
            }
        }

        assertThat(entries).isEqualTo(EXPECTED_ENTRIES);
        assertThat(studentsCsv.toString()).contains("Riya Sharma").contains("R1");
        assertThat(noticesCsv.toString()).contains("Independence Day");
    }

    @Test
    void nonAdminIsForbidden() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(teacherToken);
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/data-export", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
