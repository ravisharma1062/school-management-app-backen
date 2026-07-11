package com.school.app.notice;

import com.school.app.auth.AuthResponse;
import com.school.app.auth.LoginRequest;
import com.school.app.common.AbstractIntegrationTest;
import com.school.app.user.Role;
import com.school.app.user.User;
import com.school.app.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NoticeArchiveIntegrationTest extends AbstractIntegrationTest {

    private static final String TEST_PASSWORD = "Password@123";

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private NoticeRepository noticeRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private User teacher;
    private User admin;
    private Notice notice;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        teacher = userRepository.save(User.builder()
                .name("Teacher " + suffix)
                .email("teacher-notice-arch-" + suffix + "@school.app")
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .role(Role.TEACHER)
                .build());

        admin = userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.ADMIN)
                .findFirst()
                .orElseThrow();

        notice = noticeRepository.save(Notice.builder()
                .title("Archivable Notice " + suffix)
                .description("desc")
                .targetRole(TargetRole.ALL)
                .createdBy(admin)
                .build());
    }

    @Test
    void adminCanArchiveAndRestoreNotice() {
        HttpHeaders headers = adminHeaders();

        ResponseEntity<NoticeDto> archiveResponse = restTemplate.exchange(
                "/api/v1/notices/" + notice.getId() + "/archive", HttpMethod.PATCH,
                new HttpEntity<>(headers), NoticeDto.class);
        assertThat(archiveResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(archiveResponse.getBody()).isNotNull();
        assertThat(archiveResponse.getBody().active()).isFalse();

        ResponseEntity<NoticeDto> restoreResponse = restTemplate.exchange(
                "/api/v1/notices/" + notice.getId() + "/restore", HttpMethod.PATCH,
                new HttpEntity<>(headers), NoticeDto.class);
        assertThat(restoreResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restoreResponse.getBody()).isNotNull();
        assertThat(restoreResponse.getBody().active()).isTrue();
    }

    @Test
    void teacherCannotArchiveNotice() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/notices/" + notice.getId() + "/archive", HttpMethod.PATCH,
                new HttpEntity<>(authHeaders(teacher)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void archivedNoticeExcludedFromDefaultListButIncludedWithIncludeArchived() {
        HttpHeaders headers = adminHeaders();
        restTemplate.exchange(
                "/api/v1/notices/" + notice.getId() + "/archive", HttpMethod.PATCH,
                new HttpEntity<>(headers), NoticeDto.class);

        ResponseEntity<String> defaultList = restTemplate.exchange(
                "/api/v1/notices?size=200&role=ALL", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(defaultList.getBody()).isNotNull();
        assertThat(defaultList.getBody()).doesNotContain(notice.getId().toString());

        ResponseEntity<String> fullList = restTemplate.exchange(
                "/api/v1/notices?size=200&role=ALL&includeArchived=true", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        assertThat(fullList.getBody()).isNotNull();
        assertThat(fullList.getBody()).contains(notice.getId().toString());
    }

    private HttpHeaders authHeaders(User user) {
        LoginRequest request = new LoginRequest(user.getEmail(), TEST_PASSWORD);
        AuthResponse response = restTemplate.postForObject("/api/v1/auth/login", request, AuthResponse.class);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(response.accessToken());
        return headers;
    }

    private HttpHeaders adminHeaders() {
        LoginRequest request = new LoginRequest("admin@school.app", "Admin@123");
        AuthResponse response = restTemplate.postForObject("/api/v1/auth/login", request, AuthResponse.class);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(response.accessToken());
        return headers;
    }
}
