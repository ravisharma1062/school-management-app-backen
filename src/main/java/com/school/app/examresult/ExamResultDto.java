package com.school.app.examresult;

import java.math.BigDecimal;
import java.util.UUID;

public record ExamResultDto(
        UUID id,
        UUID studentId,
        String subject,
        String examName,
        BigDecimal marksObtained,
        BigDecimal maxMarks,
        String grade,
        String term
) {
}
