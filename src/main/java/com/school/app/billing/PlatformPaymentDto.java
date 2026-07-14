package com.school.app.billing;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PlatformPaymentDto(
        UUID id,
        UUID schoolId,
        String schoolName,
        BigDecimal amount,
        PaymentMethod method,
        String referenceNumber,
        LocalDate periodStart,
        LocalDate periodEnd,
        PaymentClaimStatus status,
        String submittedByEmail,
        Instant submittedAt,
        Instant verifiedAt,
        String notes
) {
}
