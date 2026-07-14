package com.school.app.billing;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PaymentClaimDto(
        UUID id,
        BigDecimal amount,
        PaymentMethod method,
        String referenceNumber,
        LocalDate periodStart,
        LocalDate periodEnd,
        PaymentClaimStatus status,
        Instant submittedAt,
        Instant verifiedAt,
        String notes
) {
}
