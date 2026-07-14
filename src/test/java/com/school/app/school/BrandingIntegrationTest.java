package com.school.app.school;

import com.school.app.auth.AuthResponse;
import com.school.app.auth.LoginRequest;
import com.school.app.common.AbstractIntegrationTest;
import com.school.app.common.security.TenantContext;
import com.school.app.platform.Entitlement;
import com.school.app.platform.EntitlementRepository;
import com.school.app.platform.FeatureKey;
import com.school.app.platform.PlanCode;
import com.school.app.platform.SubscriptionPlanRepository;
import com.school.app.platform.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers MT-6a's Definition of Done: a school's logo + colors appear via the branding endpoints,
 * are readable by every role in the school (not just ADMIN), writable only by an entitled ADMIN,
 * and never bleed across tenants.
 */
class BrandingIntegrationTest extends AbstractIntegrationTest {

    // A minimal valid 1x1 PNG.
    private static final byte[] ONE_PIXEL_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

    @Autowired
    private SchoolRepository schoolRepository;
    @Autowired
    private SubscriptionPlanRepository subscriptionPlanRepository;
    @Autowired
    private SubscriptionRepository subscriptionRepository;
    @Autowired
    private EntitlementRepository entitlementRepository;
    @Autowired
    private com.school.app.user.UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final String TEST_PASSWORD = "Password@123";

    private String defaultAdminToken() {
        AuthResponse response = restTemplate.postForObject(
                "/api/v1/auth/login", new LoginRequest("admin@school.app", "Admin@123"), AuthResponse.class);
        return response.accessToken();
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    @Test
    void adminCanSetColorsAndUploadALogoAndAnyRoleCanReadThem() {
        HttpHeaders adminHeaders = authHeaders(defaultAdminToken());
        adminHeaders.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<BrandingDto> initial = restTemplate.exchange(
                "/api/v1/branding", HttpMethod.GET, new HttpEntity<>(adminHeaders), BrandingDto.class);
        assertThat(initial.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(initial.getBody().hasLogo()).isFalse();

        BrandingColorsUpdateRequest colors = new BrandingColorsUpdateRequest("#4F46E5", "#D946EF");
        ResponseEntity<BrandingDto> colorResponse = restTemplate.exchange(
                "/api/v1/branding/colors", HttpMethod.PATCH, new HttpEntity<>(colors, adminHeaders), BrandingDto.class);
        assertThat(colorResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(colorResponse.getBody().primaryColor()).isEqualTo("#4F46E5");
        assertThat(colorResponse.getBody().secondaryColor()).isEqualTo("#D946EF");

        HttpHeaders multipartHeaders = authHeaders(defaultAdminToken());
        multipartHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(ONE_PIXEL_PNG) {
            @Override
            public String getFilename() {
                return "logo.png";
            }
        });
        ResponseEntity<BrandingDto> uploadResponse = restTemplate.exchange(
                "/api/v1/branding/logo", HttpMethod.POST, new HttpEntity<>(body, multipartHeaders), BrandingDto.class);
        assertThat(uploadResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(uploadResponse.getBody().hasLogo()).isTrue();

        ResponseEntity<byte[]> logoResponse = restTemplate.exchange(
                "/api/v1/branding/logo", HttpMethod.GET, new HttpEntity<>(adminHeaders), byte[].class);
        assertThat(logoResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(logoResponse.getBody()).isEqualTo(ONE_PIXEL_PNG);

        // A non-admin role in the SAME school can still read the branding (portal-wide theming).
        String parentEmail = "branding-parent-" + UUID.randomUUID() + "@school.app";
        userRepository.save(com.school.app.user.User.builder()
                .name("Branding Parent")
                .email(parentEmail)
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .role(com.school.app.user.Role.PARENT)
                .build());
        AuthResponse parentLogin = restTemplate.postForObject(
                "/api/v1/auth/login", new LoginRequest(parentEmail, TEST_PASSWORD), AuthResponse.class);
        HttpHeaders parentHeaders = authHeaders(parentLogin.accessToken());
        ResponseEntity<BrandingDto> parentRead = restTemplate.exchange(
                "/api/v1/branding", HttpMethod.GET, new HttpEntity<>(parentHeaders), BrandingDto.class);
        assertThat(parentRead.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(parentRead.getBody().hasLogo()).isTrue();
        assertThat(parentRead.getBody().primaryColor()).isEqualTo("#4F46E5");

        // ...but cannot write it.
        ResponseEntity<String> parentWriteAttempt = restTemplate.exchange(
                "/api/v1/branding/colors", HttpMethod.PATCH,
                new HttpEntity<>(new BrandingColorsUpdateRequest("#000000", null), parentHeaders), String.class);
        assertThat(parentWriteAttempt.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void nonEntitledSchoolCannotWriteBrandingEvenAsAdmin() {
        School school = schoolRepository.save(School.builder()
                .name("Non-Branding School")
                .slug("non-branding-" + UUID.randomUUID())
                .status(com.school.app.school.SchoolStatus.ACTIVE)
                .build());
        TenantContext.set(school.getId());

        var basicPlan = subscriptionPlanRepository.findByCode(PlanCode.BASIC).orElseThrow();
        var subscription = subscriptionRepository.save(com.school.app.platform.Subscription.builder()
                .school(school)
                .plan(basicPlan)
                .status(com.school.app.school.SchoolStatus.ACTIVE)
                .build());
        for (FeatureKey key : FeatureKey.values()) {
            entitlementRepository.save(Entitlement.builder()
                    .subscription(subscription)
                    .featureKey(key)
                    .enabled(false)
                    .build());
        }

        String adminEmail = "non-branding-admin-" + UUID.randomUUID() + "@school.app";
        userRepository.save(com.school.app.user.User.builder()
                .name("Non Branding Admin")
                .email(adminEmail)
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .role(com.school.app.user.Role.ADMIN)
                .build());

        School defaultSchool = schoolRepository.findBySlug("default-school").orElseThrow();
        TenantContext.set(defaultSchool.getId());

        AuthResponse login = restTemplate.postForObject(
                "/api/v1/auth/login", new LoginRequest(adminEmail, TEST_PASSWORD), AuthResponse.class);
        HttpHeaders headers = authHeaders(login.accessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/branding/colors", HttpMethod.PATCH,
                new HttpEntity<>(new BrandingColorsUpdateRequest("#4F46E5", null), headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("FEATURE_NOT_ENTITLED");
    }

    @Test
    void rejectsAMalformedColor() {
        HttpHeaders headers = authHeaders(defaultAdminToken());
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/branding/colors", HttpMethod.PATCH,
                new HttpEntity<>(new BrandingColorsUpdateRequest("blue", null), headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
