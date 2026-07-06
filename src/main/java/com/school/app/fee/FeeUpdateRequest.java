package com.school.app.fee;

import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

public record FeeUpdateRequest(
        @DecimalMin(value = "0") BigDecimal amountPaid,
        FeeStatus status
) {
}
