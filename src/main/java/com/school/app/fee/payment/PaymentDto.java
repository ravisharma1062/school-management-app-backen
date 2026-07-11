package com.school.app.fee.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentDto(
        UUID id,
        UUID feeId,
        BigDecimal amount,
        String gatewayOrderId,
        String gatewayPaymentId,
        PaymentStatus status,
        UUID initiatedBy,
        Instant paidAt,
        Instant createdAt
) {
}
