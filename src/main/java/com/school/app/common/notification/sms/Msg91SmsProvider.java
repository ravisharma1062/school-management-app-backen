package com.school.app.common.notification.sms;

import com.school.app.common.exception.NotConfiguredException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sends SMS via Msg91's Send SMS v5 API. Until {@code MSG91_AUTH_KEY}/{@code MSG91_SENDER_ID}
 * are set, {@link #send} throws {@link NotConfiguredException} instead of attempting a doomed
 * HTTP call — the school's DLT registration (sender ID + template pre-approval) has a 1-2 week
 * lead time, so this provider is expected to run unconfigured for a while.
 */
@Slf4j
@Service
public class Msg91SmsProvider implements SmsProvider {

    private static final String ENDPOINT = "https://control.msg91.com/api/v5/flow/";

    private final String authKey;
    private final String senderId;
    private final String route;
    private final RestTemplate restTemplate = new RestTemplate();

    public Msg91SmsProvider(
            @Value("${app.notification.sms.msg91.auth-key:}") String authKey,
            @Value("${app.notification.sms.msg91.sender-id:}") String senderId,
            @Value("${app.notification.sms.msg91.route:4}") String route) {
        this.authKey = authKey;
        this.senderId = senderId;
        this.route = route;
    }

    @Override
    public void send(String toPhone, String message) {
        if (authKey.isBlank() || senderId.isBlank()) {
            throw new NotConfiguredException("Msg91 is not configured (MSG91_AUTH_KEY/MSG91_SENDER_ID unset)");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("authkey", authKey);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sender", senderId);
        body.put("route", route);
        body.put("mobiles", toPhone);
        body.put("message", message);

        try {
            ResponseEntity<String> response =
                    restTemplate.postForEntity(ENDPOINT, new HttpEntity<>(body, headers), String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException("Msg91 returned " + response.getStatusCode());
            }
        } catch (RestClientException e) {
            throw new IllegalStateException("Failed to send SMS via Msg91: " + e.getMessage(), e);
        }
    }
}
