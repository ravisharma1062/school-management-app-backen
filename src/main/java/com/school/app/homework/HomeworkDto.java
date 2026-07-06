package com.school.app.homework;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record HomeworkDto(
        UUID id,
        String studentClass,
        String section,
        String subject,
        String title,
        String description,
        LocalDate dueDate,
        UUID createdBy,
        Instant createdAt
) {
}
