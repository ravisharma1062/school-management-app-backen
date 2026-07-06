package com.school.app.homework;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record HomeworkCreateRequest(
        @NotBlank String studentClass,
        @NotBlank String section,
        @NotBlank String subject,
        @NotBlank String title,
        String description,
        @NotNull @FutureOrPresent LocalDate dueDate
) {
}
