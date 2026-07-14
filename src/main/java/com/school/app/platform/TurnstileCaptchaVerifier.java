package com.school.app.platform;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Verifies Cloudflare Turnstile tokens server-side. Until {@code CAPTCHA_TURNSTILE_SECRET_KEY} is
 * set, {@link #verify} passes everything through (see {@link CaptchaVerifier}'s Javadoc for why) —
 * a one-time warning is logged the first time this happens so it doesn't go unnoticed.
 */
@Slf4j
@Service
public class TurnstileCaptchaVerifier implements CaptchaVerifier {

    private static final String VERIFY_ENDPOINT = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    private final String secretKey;
    private final RestTemplate restTemplate = new RestTemplate();
    private volatile boolean warnedUnconfigured = false;

    public TurnstileCaptchaVerifier(@Value("${app.captcha.turnstile.secret-key:}") String secretKey) {
        this.secretKey = secretKey;
    }

    @Override
    public boolean verify(String token, String remoteIp) {
        if (secretKey.isBlank()) {
            if (!warnedUnconfigured) {
                log.warn("CAPTCHA_TURNSTILE_SECRET_KEY is not set — public signup verification is running "
                        + "WIDE OPEN (every token passes). Set it before any non-local deployment.");
                warnedUnconfigured = true;
            }
            return true;
        }
        if (token == null || token.isBlank()) {
            return false;
        }

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("secret", secretKey);
        body.add("response", token);
        if (remoteIp != null && !remoteIp.isBlank()) {
            body.add("remoteip", remoteIp);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    VERIFY_ENDPOINT, new HttpEntity<>(body, headers), Map.class);
            return response != null && Boolean.TRUE.equals(response.get("success"));
        } catch (RestClientException e) {
            log.error("Turnstile verification request failed: {}", e.getMessage());
            return false;
        }
    }
}
