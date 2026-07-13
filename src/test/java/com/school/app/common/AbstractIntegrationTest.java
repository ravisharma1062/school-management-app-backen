package com.school.app.common;

import com.school.app.common.security.TenantContext;
import com.school.app.school.School;
import com.school.app.school.SchoolRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    /**
     * A single Postgres container shared by every {@code *IntegrationTest} in the JVM run.
     *
     * <p>Using the Testcontainers "singleton container" pattern (started once here, reused by all
     * subclasses, torn down at JVM shutdown) instead of the {@code @Testcontainers}/{@code @Container}
     * per-class lifecycle avoids the Docker Desktop port-forwarding failures — surfacing as
     * {@code HikariPool ... connection refused} — that occur when many containers start and stop
     * within one test run.
     */
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("school_app_test")
            .withUsername("school_app")
            .withPassword("school_app");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // Keep file uploads made by tests out of the repo's ./storage dir.
        registry.add("app.storage.local.base-dir",
                () -> System.getProperty("java.io.tmpdir") + "/school-app-test-storage");
    }

    @Autowired
    protected TestRestTemplate restTemplate;
    @Autowired
    private SchoolRepository schoolRepository;

    /**
     * Test fixtures built by calling a repository directly (bypassing HTTP/login) run on this
     * JUnit thread, which {@code JwtAuthFilter} never touches — every {@code @TenantId} entity
     * insert would otherwise try to write the nil sentinel and fail its FK to {@code schools}.
     * Scope this thread to the one school Flyway's V16 migration created (JUnit superclass
     * {@code @BeforeEach} methods run before subclass ones, so this is set before any subclass
     * {@code setUp()} saves a fixture), matching what a real request for the seeded admin would
     * resolve to.
     */
    @BeforeEach
    void setDefaultTenantContext() {
        School defaultSchool = schoolRepository.findBySlug("default-school")
                .orElseThrow(() -> new IllegalStateException("Flyway's default school seed (V16) is missing"));
        TenantContext.set(defaultSchool.getId());
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @BeforeEach
    void enablePatchAndErrorBodySupport() {
        // The default SimpleClientHttpRequestFactory (HttpURLConnection) cannot issue PATCH requests
        // and cannot read the body of an error response; the JDK 11+ HttpClient factory supports both.
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }
}
