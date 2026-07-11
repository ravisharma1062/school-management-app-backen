package com.school.app.fee.payment;

import com.school.app.common.exception.NotConfiguredException;

import java.math.BigDecimal;

public interface PaymentGatewayProvider {

    /**
     * Creates a payment order with the gateway.
     *
     * @throws NotConfiguredException if the gateway has no API credentials configured yet
     */
    GatewayOrder createOrder(BigDecimal amount, String currency, String receipt);

    /**
     * Verifies that a webhook payload's signature was produced by this gateway using our
     * configured webhook secret. Returns false (never throws) for any invalid/unverifiable input,
     * including when no webhook secret is configured — an unconfigured webhook cannot be trusted.
     */
    boolean verifyWebhookSignature(String payload, String signature);

    record GatewayOrder(String orderId, String currency, long amountInSmallestUnit, String gatewayKeyId) {
    }
}
