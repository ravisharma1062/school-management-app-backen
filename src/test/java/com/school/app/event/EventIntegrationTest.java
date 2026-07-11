package com.school.app.event;

import com.school.app.auth.AuthResponse;
import com.school.app.auth.LoginRequest;
import com.school.app.common.AbstractIntegrationTest;
import com.school.app.user.Role;
import com.school.app.user.User;
import com.school.app.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EventIntegrationTest extends AbstractIntegrationTest {

    private static final String TEST_PASSWORD = "Password@123";

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private User parent;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        parent = userRepository.save(User.builder()
                .name("Event Parent " + suffix)
                .email("event-parent-" + suffix + "@school.app")
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .role(Role.PARENT)
                .build());
    }

    @Test
    void parentCannotCreateAnEvent() {
        ResponseEntity<String> response = createEvent(authHeaders(parent), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminCreatesEventAndItAppearsInRangeForParent() {
        EventDto created = createEvent(adminHeaders(), EventDto.class).getBody();
        assertThat(created).isNotNull();
        assertThat(created.myRsvpStatus()).isNull();

        ResponseEntity<List<EventDto>> listResponse = restTemplate.exchange(
                "/api/v1/events?range=10", HttpMethod.GET,
                new HttpEntity<>(authHeaders(parent)),
                new ParameterizedTypeReference<>() {
                });

        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).extracting(EventDto::id).contains(created.id());
    }

    @Test
    void parentRsvpsAndSeesTheirOwnStatusInTheListing() {
        EventDto created = createEvent(adminHeaders(), EventDto.class).getBody();

        HttpHeaders parentHeaders = authHeaders(parent);
        parentHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<EventRsvpDto> rsvpResponse = restTemplate.exchange(
                "/api/v1/events/" + created.id() + "/rsvp", HttpMethod.POST,
                new HttpEntity<>(new EventRsvpRequest(RsvpStatus.GOING), parentHeaders), EventRsvpDto.class);
        assertThat(rsvpResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rsvpResponse.getBody().status()).isEqualTo(RsvpStatus.GOING);
        assertThat(rsvpResponse.getBody().userName()).isEqualTo(parent.getName());

        ResponseEntity<List<EventDto>> listResponse = restTemplate.exchange(
                "/api/v1/events?range=10", HttpMethod.GET,
                new HttpEntity<>(authHeaders(parent)),
                new ParameterizedTypeReference<>() {
                });
        EventDto listed = listResponse.getBody().stream().filter(e -> e.id().equals(created.id())).findFirst().orElseThrow();
        assertThat(listed.myRsvpStatus()).isEqualTo(RsvpStatus.GOING);
    }

    @Test
    void rsvpCanBeChangedAndAdminSeesTheUpdatedRsvpList() {
        EventDto created = createEvent(adminHeaders(), EventDto.class).getBody();
        submitRsvp(created.id(), RsvpStatus.MAYBE, parent);
        submitRsvp(created.id(), RsvpStatus.NOT_GOING, parent);

        ResponseEntity<List<EventRsvpDto>> rsvpsResponse = restTemplate.exchange(
                "/api/v1/events/" + created.id() + "/rsvps", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {
                });

        assertThat(rsvpsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rsvpsResponse.getBody()).hasSize(1);
        assertThat(rsvpsResponse.getBody().get(0).status()).isEqualTo(RsvpStatus.NOT_GOING);
    }

    @Test
    void nonAdminCannotViewRsvpList() {
        EventDto created = createEvent(adminHeaders(), EventDto.class).getBody();

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/events/" + created.id() + "/rsvps", HttpMethod.GET,
                new HttpEntity<>(authHeaders(parent)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private void submitRsvp(UUID eventId, RsvpStatus status, User user) {
        HttpHeaders headers = authHeaders(user);
        headers.setContentType(MediaType.APPLICATION_JSON);
        restTemplate.exchange(
                "/api/v1/events/" + eventId + "/rsvp", HttpMethod.POST,
                new HttpEntity<>(new EventRsvpRequest(status), headers), EventRsvpDto.class);
    }

    private <T> ResponseEntity<T> createEvent(HttpHeaders headers, Class<T> responseType) {
        headers.setContentType(MediaType.APPLICATION_JSON);
        EventCreateRequest request = new EventCreateRequest(
                "Sports Day", "Annual sports day", LocalDate.now().plusDays(5), "Main Ground");
        return restTemplate.exchange(
                "/api/v1/events", HttpMethod.POST, new HttpEntity<>(request, headers), responseType);
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
