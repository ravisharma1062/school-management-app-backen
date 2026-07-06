package com.school.app.fee;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record FeeDto(
        UUID id,
        UUID studentId,
        String term,
        BigDecimal amountDue,
        BigDecimal amountPaid,
        FeeStatus status,
        LocalDate dueDate
) {
}
