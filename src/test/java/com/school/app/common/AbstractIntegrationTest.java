package com.school.app.common;

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
    }

    @Autowired
    protected TestRestTemplate restTemplate;

    @BeforeEach
    void enablePatchAndErrorBodySupport() {
        // The default SimpleClientHttpRequestFactory (HttpURLConnection) cannot issue PATCH requests
        // and cannot read the body of an error response; the JDK 11+ HttpClient factory supports both.
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }
}
