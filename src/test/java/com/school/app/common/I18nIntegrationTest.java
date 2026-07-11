package com.school.app.common;

import com.school.app.auth.LoginRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class I18nIntegrationTest extends AbstractIntegrationTest {

    @Test
    void validationErrorsAreLocalizedByAcceptLanguageHeader() {
        LoginRequest blankRequest = new LoginRequest("", "");

        ResponseEntity<Map> englishResponse = postLogin(blankRequest, "en");
        ResponseEntity<Map> hindiResponse = postLogin(blankRequest, "hi");

        assertThat(englishResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(hindiResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(englishResponse.getBody().get("message")).isEqualTo("Validation failed");
        assertThat(hindiResponse.getBody().get("message")).isEqualTo("सत्यापन विफल");

        assertThat(englishResponse.getBody().get("fieldErrors").toString()).contains("must not be blank");
        assertThat(hindiResponse.getBody().get("fieldErrors").toString()).contains("खाली नहीं होना चाहिए");
    }

    @Test
    void badCredentialsMessageIsLocalized() {
        LoginRequest wrongPassword = new LoginRequest("admin@school.app", "definitely-wrong");

        ResponseEntity<Map> hindiResponse = postLogin(wrongPassword, "hi");

        assertThat(hindiResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(hindiResponse.getBody().get("message")).isEqualTo("अमान्य ईमेल या पासवर्ड");
    }

    private ResponseEntity<Map> postLogin(LoginRequest request, String language) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.ACCEPT_LANGUAGE, language);
        return restTemplate.exchange(
                "/api/v1/auth/login", HttpMethod.POST, new HttpEntity<>(request, headers), Map.class);
    }
}
