package com.school.app.cucumber.support;

import io.cucumber.java.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Resets the database to a known clean state before every scenario so that seeded data and
 * count-based assertions are deterministic (all scenarios share one Postgres container).
 *
 * <p>The Flyway-seeded {@code admin@school.app} user is preserved so admin login keeps working.
 */
public class Hooks {

    private static final String[] TABLES_IN_DELETE_ORDER = {
            "attendance", "exam_results", "homework", "notices", "timetable", "fees", "teachers", "students"
    };

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private TestRestTemplate restTemplate;

    @Before
    public void resetDatabase() {
        for (String table : TABLES_IN_DELETE_ORDER) {
            jdbcTemplate.update("DELETE FROM " + table);
        }
        jdbcTemplate.update("DELETE FROM users WHERE email <> 'admin@school.app'");
    }

    /**
     * The default {@code SimpleClientHttpRequestFactory} (JDK {@code HttpURLConnection}) cannot
     * issue PATCH requests and cannot read the body of an error response in streaming mode. Swap in
     * the JDK 11+ HttpClient factory, which supports both.
     */
    @Before
    public void useHttpClientThatSupportsPatch() {
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }
}
