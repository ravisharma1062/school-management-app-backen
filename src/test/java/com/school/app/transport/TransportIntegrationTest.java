package com.school.app.transport;

import com.school.app.auth.AuthResponse;
import com.school.app.auth.LoginRequest;
import com.school.app.common.AbstractIntegrationTest;
import com.school.app.student.Student;
import com.school.app.student.StudentRepository;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TransportIntegrationTest extends AbstractIntegrationTest {

    private static final String TEST_PASSWORD = "Password@123";

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private User parent;
    private User otherParent;
    private Student child;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        parent = userRepository.save(User.builder()
                .name("Transport Parent " + suffix)
                .email("transport-parent-" + suffix + "@school.app")
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .role(Role.PARENT)
                .build());

        otherParent = userRepository.save(User.builder()
                .name("Other Parent " + suffix)
                .email("transport-other-" + suffix + "@school.app")
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .role(Role.PARENT)
                .build());

        child = studentRepository.save(Student.builder()
                .name("Transport Child " + suffix)
                .rollNo("TR-" + suffix)
                .studentClass("6")
                .section("A")
                .dob(LocalDate.of(2013, 1, 1))
                .parent(parent)
                .build());
    }

    @Test
    void nonAdminCannotCreateARoute() {
        ResponseEntity<String> response = createRoute(authHeaders(parent), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminCreatesARouteWithStopsAndGetsALocationToken() {
        BusRouteAdminDto route = createRoute(adminHeaders(), BusRouteAdminDto.class).getBody();

        assertThat(route).isNotNull();
        assertThat(route.locationToken()).isNotBlank();
        assertThat(route.stops()).hasSize(2);
        assertThat(route.stops().get(0).stopOrder()).isEqualTo(0);
    }

    @Test
    void deviceCanPushLocationWithAValidTokenButNotAnInvalidOne() {
        BusRouteAdminDto route = createRoute(adminHeaders(), BusRouteAdminDto.class).getBody();

        HttpHeaders badHeaders = new HttpHeaders();
        badHeaders.setContentType(MediaType.APPLICATION_JSON);
        badHeaders.set("X-Location-Token", "not-the-real-token");
        ResponseEntity<String> badPush = restTemplate.exchange(
                "/api/v1/transport/routes/" + route.id() + "/location", HttpMethod.POST,
                new HttpEntity<>(new LocationPushRequest(12.9716, 77.5946), badHeaders), String.class);
        assertThat(badPush.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        HttpHeaders goodHeaders = new HttpHeaders();
        goodHeaders.setContentType(MediaType.APPLICATION_JSON);
        goodHeaders.set("X-Location-Token", route.locationToken());
        ResponseEntity<Void> goodPush = restTemplate.exchange(
                "/api/v1/transport/routes/" + route.id() + "/location", HttpMethod.POST,
                new HttpEntity<>(new LocationPushRequest(12.9716, 77.5946), goodHeaders), Void.class);
        assertThat(goodPush.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void parentWithAssignedChildSeesLocationButUnrelatedParentIsForbidden() {
        BusRouteAdminDto route = createRoute(adminHeaders(), BusRouteAdminDto.class).getBody();
        UUID stopId = route.stops().get(0).id();

        restTemplate.exchange(
                "/api/v1/transport/students/" + child.getId(), HttpMethod.PUT,
                new HttpEntity<>(new StudentTransportAssignRequest(route.id(), stopId), adminHeaders()),
                StudentTransportDto.class);

        HttpHeaders pushHeaders = new HttpHeaders();
        pushHeaders.setContentType(MediaType.APPLICATION_JSON);
        pushHeaders.set("X-Location-Token", route.locationToken());
        restTemplate.exchange(
                "/api/v1/transport/routes/" + route.id() + "/location", HttpMethod.POST,
                new HttpEntity<>(new LocationPushRequest(12.9716, 77.5946), pushHeaders), Void.class);

        ResponseEntity<BusLocationDto> ownerView = restTemplate.exchange(
                "/api/v1/transport/routes/" + route.id() + "/location/latest", HttpMethod.GET,
                new HttpEntity<>(authHeaders(parent)), BusLocationDto.class);
        assertThat(ownerView.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ownerView.getBody().latitude()).isEqualTo(12.9716);

        ResponseEntity<String> outsiderView = restTemplate.exchange(
                "/api/v1/transport/routes/" + route.id() + "/location/latest", HttpMethod.GET,
                new HttpEntity<>(authHeaders(otherParent)), String.class);
        assertThat(outsiderView.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void assigningAStopFromADifferentRouteIsRejected() {
        BusRouteAdminDto routeA = createRoute(adminHeaders(), BusRouteAdminDto.class).getBody();
        BusRouteAdminDto routeB = createRoute(adminHeaders(), BusRouteAdminDto.class).getBody();
        UUID stopFromRouteB = routeB.stops().get(0).id();

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/transport/students/" + child.getId(), HttpMethod.PUT,
                new HttpEntity<>(new StudentTransportAssignRequest(routeA.id(), stopFromRouteB), adminHeaders()),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void reassigningAStudentUpdatesTheExistingAssignmentInsteadOfDuplicating() {
        BusRouteAdminDto route = createRoute(adminHeaders(), BusRouteAdminDto.class).getBody();
        UUID firstStop = route.stops().get(0).id();
        UUID secondStop = route.stops().get(1).id();

        restTemplate.exchange(
                "/api/v1/transport/students/" + child.getId(), HttpMethod.PUT,
                new HttpEntity<>(new StudentTransportAssignRequest(route.id(), firstStop), adminHeaders()),
                StudentTransportDto.class);
        ResponseEntity<StudentTransportDto> second = restTemplate.exchange(
                "/api/v1/transport/students/" + child.getId(), HttpMethod.PUT,
                new HttpEntity<>(new StudentTransportAssignRequest(route.id(), secondStop), adminHeaders()),
                StudentTransportDto.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody().stopId()).isEqualTo(secondStop);

        ResponseEntity<StudentTransportDto> fetched = restTemplate.exchange(
                "/api/v1/transport/students/" + child.getId(), HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), StudentTransportDto.class);
        assertThat(fetched.getBody().stopId()).isEqualTo(secondStop);
        assertThat(fetched.getBody().routeName()).isEqualTo(route.name());
    }

    private <T> ResponseEntity<T> createRoute(HttpHeaders headers, Class<T> responseType) {
        headers.setContentType(MediaType.APPLICATION_JSON);
        BusRouteCreateRequest request = new BusRouteCreateRequest(
                "Route " + UUID.randomUUID().toString().substring(0, 6),
                "A test route",
                List.of(
                        new BusStopCreateRequest("Stop A", 0, 12.9, 77.5),
                        new BusStopCreateRequest("Stop B", 1, 12.95, 77.55)));
        return restTemplate.exchange(
                "/api/v1/transport/routes", HttpMethod.POST, new HttpEntity<>(request, headers), responseType);
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
