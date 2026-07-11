package com.school.app.library;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record BookIssueDto(
        UUID id,
        UUID bookId,
        String bookTitle,
        UUID studentId,
        String studentName,
        LocalDate issuedAt,
        LocalDate dueDate,
        LocalDate returnedAt,
        BigDecimal fineAmount,
        BookIssueStatus status
) {
}
