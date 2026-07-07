package com.school.app.cucumber;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Wires the Cucumber engine into a single Spring Boot application context for the whole run.
 *
 * <p>Unlike the JUnit {@code *IntegrationTest} classes (which use {@code @Testcontainers} to
 * start/stop a container per test class), Cucumber shares one context across every scenario, so we
 * start a single Postgres container using the Testcontainers "singleton container" pattern: it is
 * started once here and reused by all features. The JVM tears it down on shutdown.
 */
@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class CucumberSpringConfiguration {

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
}
