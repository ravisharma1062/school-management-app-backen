package com.school.app.timetable;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.util.UUID;

public record TimetableCreateRequest(
        @NotBlank String studentClass,
        @NotBlank String section,
        @NotNull DayOfWeek dayOfWeek,
        @Min(1) @Max(12) int period,
        @NotBlank String subject,
        @NotNull UUID teacherId
) {
}
