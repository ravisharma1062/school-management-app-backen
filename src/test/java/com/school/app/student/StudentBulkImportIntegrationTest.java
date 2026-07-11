package com.school.app.student;

import com.school.app.auth.AuthResponse;
import com.school.app.auth.LoginRequest;
import com.school.app.common.AbstractIntegrationTest;
import com.school.app.user.Role;
import com.school.app.user.User;
import com.school.app.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StudentBulkImportIntegrationTest extends AbstractIntegrationTest {

    private static final String TEST_PASSWORD = "Password@123";

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private User teacher;
    private User parent;
    private String suffix;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 8);

        teacher = userRepository.save(User.builder()
                .name("Teacher " + suffix)
                .email("teacher-bulk-" + suffix + "@school.app")
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .role(Role.TEACHER)
                .build());

        parent = userRepository.save(User.builder()
                .name("Parent " + suffix)
                .email("parent-bulk-" + suffix + "@school.app")
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .role(Role.PARENT)
                .build());
    }

    @Test
    void adminImportsValidRowsIncludingOptionalParentEmail() {
        String csv = "name,rollNo,studentClass,section,dob,parentEmail\n"
                + "Row One," + suffix + "-01,9,B,2013-01-01," + parent.getEmail() + "\n"
                + "Row Two," + suffix + "-02,9,B,2013-02-02,\n";

        ResponseEntity<BulkImportResult> response = upload(csv, adminHeaders());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().totalRows()).isEqualTo(2);
        assertThat(response.getBody().successCount()).isEqualTo(2);
        assertThat(response.getBody().failureCount()).isZero();
        assertThat(response.getBody().errors()).isEmpty();
    }

    @Test
    void badRowsAreReportedWithoutFailingTheWholeBatch() {
        String csv = "name,rollNo,studentClass,section,dob,parentEmail\n"
                + "Good Row," + suffix + "-10,9,C,2013-01-01,\n"
                + "Bad Date Row," + suffix + "-11,9,C,not-a-date,\n"
                + "Unknown Parent Row," + suffix + "-12,9,C,2013-01-01,nobody-" + suffix + "@school.app\n"
                + "Duplicate In File," + suffix + "-10,9,C,2013-01-01,\n";

        ResponseEntity<BulkImportResult> response = upload(csv, adminHeaders());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().totalRows()).isEqualTo(4);
        assertThat(response.getBody().successCount()).isEqualTo(1);
        assertThat(response.getBody().failureCount()).isEqualTo(3);
        assertThat(response.getBody().errors()).hasSize(3);
        // Row numbers are 1-indexed with the header as row 1, so the four data rows are 2-5.
        assertThat(response.getBody().errors()).extracting(BulkImportResult.RowError::row)
                .containsExactly(3, 4, 5);
    }

    @Test
    void missingRequiredColumnIsRejectedBeforeProcessingAnyRow() {
        String csv = "name,rollNo,section,dob\nNo Class Row,R-1,A,2013-01-01\n";

        ResponseEntity<String> response = upload(csv, adminHeaders(), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("studentClass");
    }

    @Test
    void teacherCannotBulkImport() {
        String csv = "name,rollNo,studentClass,section,dob\nBlocked," + suffix + "-99,9,D,2013-01-01\n";

        ResponseEntity<String> response = upload(csv, authHeaders(teacher), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private ResponseEntity<BulkImportResult> upload(String csv, HttpHeaders headers) {
        return upload(csv, headers, BulkImportResult.class);
    }

    private <T> ResponseEntity<T> upload(String csv, HttpHeaders headers, Class<T> responseType) {
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ByteArrayResource fileResource = new ByteArrayResource(csv.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "students.csv";
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", fileResource);

        return restTemplate.exchange(
                "/api/v1/students/bulk-import", HttpMethod.POST, new HttpEntity<>(body, headers), responseType);
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
