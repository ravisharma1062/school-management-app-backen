package com.school.app.student;

import com.school.app.auth.AuthResponse;
import com.school.app.auth.LoginRequest;
import com.school.app.common.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StudentSearchIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private StudentRepository studentRepository;

    private String suffix;
    private Student aarav;
    private Student diya;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 8);

        aarav = studentRepository.save(Student.builder()
                .name("AaravSearch" + suffix)
                .rollNo("SR-" + suffix + "-01")
                .studentClass("7")
                .section("A")
                .dob(LocalDate.of(2014, 1, 1))
                .build());

        diya = studentRepository.save(Student.builder()
                .name("DiyaSearch" + suffix)
                .rollNo("SR-" + suffix + "-02")
                .studentClass("8")
                .section("B")
                .dob(LocalDate.of(2013, 1, 1))
                .build());
    }

    @Test
    void filtersByNameCaseInsensitively() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/students?name=aaravsearch" + suffix, HttpMethod.GET, new HttpEntity<>(adminHeaders()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains(aarav.getId().toString());
        assertThat(response.getBody()).doesNotContain(diya.getId().toString());
    }

    @Test
    void filtersByRollNoPartialMatch() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/students?rollNo=" + suffix + "-02", HttpMethod.GET, new HttpEntity<>(adminHeaders()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains(diya.getId().toString());
        assertThat(response.getBody()).doesNotContain(aarav.getId().toString());
    }

    @Test
    void filtersByExactClass() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/students?studentClass=7&size=200", HttpMethod.GET, new HttpEntity<>(adminHeaders()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains(aarav.getId().toString());
        assertThat(response.getBody()).doesNotContain(diya.getId().toString());
    }

    @Test
    void combinesFiltersWithAnd() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/students?name=Search" + suffix + "&studentClass=8", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains(diya.getId().toString());
        assertThat(response.getBody()).doesNotContain(aarav.getId().toString());
    }

    private HttpHeaders adminHeaders() {
        LoginRequest request = new LoginRequest("admin@school.app", "Admin@123");
        AuthResponse response = restTemplate.postForObject("/api/v1/auth/login", request, AuthResponse.class);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(response.accessToken());
        return headers;
    }
}
