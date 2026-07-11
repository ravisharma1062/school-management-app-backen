package com.school.app.fee.payment;

import com.school.app.common.exception.NotConfiguredException;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RazorpayProviderTest {

    private static String hmacSha256Hex(String secret, String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void createOrderThrowsWhenNotConfigured() {
        RazorpayProvider provider = new RazorpayProvider("", "", "webhook-secret");

        assertThatThrownBy(() -> provider.createOrder(BigDecimal.TEN, "INR", "receipt-1"))
                .isInstanceOf(NotConfiguredException.class);
    }

    @Test
    void verifiesAGenuineWebhookSignature() throws Exception {
        String secret = "test-webhook-secret";
        String payload = "{\"event\":\"payment.captured\",\"payload\":{}}";
        String signature = hmacSha256Hex(secret, payload);

        RazorpayProvider provider = new RazorpayProvider("key_id", "key_secret", secret);

        assertThat(provider.verifyWebhookSignature(payload, signature)).isTrue();
    }

    @Test
    void rejectsATamperedPayload() throws Exception {
        String secret = "test-webhook-secret";
        String originalPayload = "{\"event\":\"payment.captured\",\"payload\":{}}";
        String signature = hmacSha256Hex(secret, originalPayload);
        String tamperedPayload = "{\"event\":\"payment.captured\",\"payload\":{\"tampered\":true}}";

        RazorpayProvider provider = new RazorpayProvider("key_id", "key_secret", secret);

        assertThat(provider.verifyWebhookSignature(tamperedPayload, signature)).isFalse();
    }

    @Test
    void rejectsASignatureComputedWithTheWrongSecret() throws Exception {
        String payload = "{\"event\":\"payment.captured\",\"payload\":{}}";
        String signature = hmacSha256Hex("wrong-secret", payload);

        RazorpayProvider provider = new RazorpayProvider("key_id", "key_secret", "test-webhook-secret");

        assertThat(provider.verifyWebhookSignature(payload, signature)).isFalse();
    }

    @Test
    void anUnconfiguredWebhookSecretNeverVerifies() {
        RazorpayProvider provider = new RazorpayProvider("key_id", "key_secret", "");

        assertThat(provider.verifyWebhookSignature("{}", "any-signature")).isFalse();
    }
}
