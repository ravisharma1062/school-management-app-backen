package com.school.app.fee.payment;

import com.school.app.common.exception.NotConfiguredException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Integrates with Razorpay's Orders API (order creation) and its documented webhook signature
 * scheme (HMAC-SHA256 of the raw request body, hex-encoded, compared to the
 * {@code X-Razorpay-Signature} header). Until {@code RAZORPAY_KEY_ID}/{@code RAZORPAY_KEY_SECRET}
 * are set, {@link #createOrder} throws {@link NotConfiguredException} — the school's payment
 * gateway business KYC has its own lead time, so this is expected to run unconfigured for a
 * while; sandbox/test-mode keys work identically to live keys against this same code path.
 */
@Slf4j
@Service
public class RazorpayProvider implements PaymentGatewayProvider {

    private static final String ORDERS_ENDPOINT = "https://api.razorpay.com/v1/orders";

    private final String keyId;
    private final String keySecret;
    private final String webhookSecret;
    private final RestTemplate restTemplate = new RestTemplate();

    public RazorpayProvider(
            @Value("${app.payment.razorpay.key-id:}") String keyId,
            @Value("${app.payment.razorpay.key-secret:}") String keySecret,
            @Value("${app.payment.razorpay.webhook-secret:}") String webhookSecret) {
        this.keyId = keyId;
        this.keySecret = keySecret;
        this.webhookSecret = webhookSecret;
    }

    @Override
    @SuppressWarnings("unchecked")
    public GatewayOrder createOrder(BigDecimal amount, String currency, String receipt) {
        if (keyId.isBlank() || keySecret.isBlank()) {
            throw new NotConfiguredException("Razorpay is not configured (RAZORPAY_KEY_ID/RAZORPAY_KEY_SECRET unset)");
        }

        long amountInSmallestUnit = amount.setScale(2, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .longValueExact();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBasicAuth(keyId, keySecret);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", amountInSmallestUnit);
        body.put("currency", currency);
        body.put("receipt", receipt);

        try {
            Map<String, Object> response = restTemplate.postForObject(
                    ORDERS_ENDPOINT, new HttpEntity<>(body, headers), Map.class);
            String orderId = response != null ? (String) response.get("id") : null;
            if (orderId == null) {
                throw new IllegalStateException("Razorpay order creation response had no 'id': " + response);
            }
            return new GatewayOrder(orderId, currency, amountInSmallestUnit, keyId);
        } catch (RestClientException e) {
            throw new IllegalStateException("Failed to create Razorpay order: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean verifyWebhookSignature(String payload, String signature) {
        if (webhookSecret.isBlank() || payload == null || signature == null) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String computed = HexFormat.of().formatHex(hash);
            return MessageDigest.isEqual(
                    computed.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("Failed to verify Razorpay webhook signature: {}", e.getMessage());
            return false;
        }
    }
}
