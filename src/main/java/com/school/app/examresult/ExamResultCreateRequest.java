package com.school.app.examresult;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record ExamResultCreateRequest(
        @NotNull UUID studentId,
        @NotBlank String subject,
        @NotBlank String examName,
        @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal marksObtained,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal maxMarks,
        @NotBlank String term
) {
}
