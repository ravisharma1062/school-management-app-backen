package com.school.app.user;

import com.school.app.auth.AuthResponse;
import com.school.app.auth.LoginRequest;
import com.school.app.common.AbstractIntegrationTest;
import com.school.app.common.security.TenantContext;
import com.school.app.platform.PlanCode;
import com.school.app.platform.ProvisionApproveRequest;
import com.school.app.platform.ProvisioningService;
import com.school.app.platform.SignupRequest;
import com.school.app.platform.SignupRequestRepository;
import com.school.app.school.School;
import com.school.app.school.SchoolRepository;
import com.school.app.school.SchoolStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers Phase MT-6e's Definition of Done: billing actions (reassigning who the billing owner is)
 * are restricted to the current billing owner; operational admins are otherwise unaffected — they
 * keep full access to run the school, they just can't reassign billing ownership themselves.
 */
class BillingOwnerIntegrationTest extends AbstractIntegrationTest {

    private static final String TEST_PASSWORD = "Password@123";

    @Autowired
    private SchoolRepository schoolRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private SignupRequestRepository signupRequestRepository;
    @Autowired
    private ProvisioningService provisioningService;

    private School school;
    private User billingOwnerAdmin;
    private User otherAdmin;
    private User teacher;

    @BeforeEach
    void seedSchoolWithTwoAdmins() {
        school = schoolRepository.save(School.builder()
                .name("Billing Owner Test School")
                .slug("billing-owner-test-" + UUID.randomUUID())
                .status(SchoolStatus.ACTIVE)
                .build());
        TenantContext.set(school.getId());

        billingOwnerAdmin = userRepository.save(User.builder()
                .name("Owner Admin")
                .email("owner-admin-" + UUID.randomUUID() + "@school.app")
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .role(Role.ADMIN)
                .billingOwner(true)
                .build());
        otherAdmin = userRepository.save(User.builder()
                .name("Other Admin")
                .email("other-admin-" + UUID.randomUUID() + "@school.app")
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .role(Role.ADMIN)
                .billingOwner(false)
                .build());
        teacher = userRepository.save(User.builder()
                .name("Some Teacher")
                .email("some-teacher-" + UUID.randomUUID() + "@school.app")
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .role(Role.TEACHER)
                .build());

        School defaultSchool = schoolRepository.findBySlug("default-school").orElseThrow();
        TenantContext.set(defaultSchool.getId());
    }

    private HttpHeaders authHeaders(User user) {
        LoginRequest request = new LoginRequest(user.getEmail(), TEST_PASSWORD);
        AuthResponse response = restTemplate.postForObject("/api/v1/auth/login", request, AuthResponse.class);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(response.accessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @Test
    void billingOwnerCanReassignToAnotherAdmin() {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/users/" + otherAdmin.getId() + "/billing-owner", HttpMethod.PATCH,
                new HttpEntity<>(authHeaders(billingOwnerAdmin)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("billingOwner")).isEqualTo(true);

        assertThat(userRepository.findById(otherAdmin.getId()).orElseThrow().isBillingOwner()).isTrue();
        assertThat(userRepository.findById(billingOwnerAdmin.getId()).orElseThrow().isBillingOwner()).isFalse();
    }

    @Test
    void nonBillingOwnerAdminCannotReassign() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/users/" + otherAdmin.getId() + "/billing-owner", HttpMethod.PATCH,
                new HttpEntity<>(authHeaders(otherAdmin)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(userRepository.findById(billingOwnerAdmin.getId()).orElseThrow().isBillingOwner()).isTrue();
    }

    @Test
    void teacherCannotReassignBillingOwner() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/users/" + teacher.getId() + "/billing-owner", HttpMethod.PATCH,
                new HttpEntity<>(authHeaders(teacher)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void reassigningBillingOwnershipToANonAdminIsRejected() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/users/" + teacher.getId() + "/billing-owner", HttpMethod.PATCH,
                new HttpEntity<>(authHeaders(billingOwnerAdmin)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void aSchoolWithNoBillingOwnerLetsAnyAdminClaimIt() {
        billingOwnerAdmin.setBillingOwner(false);
        userRepository.save(billingOwnerAdmin);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/v1/users/" + otherAdmin.getId() + "/billing-owner", HttpMethod.PATCH,
                new HttpEntity<>(authHeaders(otherAdmin)), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(userRepository.findById(otherAdmin.getId()).orElseThrow().isBillingOwner()).isTrue();
    }

    @Test
    void authMeAndUsersListIncludeBillingOwnerFlag() {
        ResponseEntity<Map> me = restTemplate.exchange(
                "/api/v1/auth/me", HttpMethod.GET, new HttpEntity<>(authHeaders(billingOwnerAdmin)), Map.class);
        assertThat(me.getBody().get("billingOwner")).isEqualTo(true);

        ResponseEntity<Map> meOther = restTemplate.exchange(
                "/api/v1/auth/me", HttpMethod.GET, new HttpEntity<>(authHeaders(otherAdmin)), Map.class);
        assertThat(meOther.getBody().get("billingOwner")).isEqualTo(false);
    }

    @Test
    void theFoundingAdminOfANewlyProvisionedSchoolDefaultsToBillingOwner() {
        SignupRequest signupRequest = signupRequestRepository.save(SignupRequest.builder()
                .schoolName("Freshly Provisioned School " + UUID.randomUUID())
                .contactName("New Admin")
                .contactEmail("new-admin-" + UUID.randomUUID() + "@example.com")
                .desiredPlan(PlanCode.BASIC)
                .wantsEmail(true)
                .wantsSms(false)
                .build());

        var outcome = provisioningService.approve(signupRequest.getId(), new ProvisionApproveRequest(null, false), null);

        User founder = userRepository.findByEmail(outcome.result().adminEmail()).orElseThrow();
        assertThat(founder.isBillingOwner()).isTrue();
    }
}
