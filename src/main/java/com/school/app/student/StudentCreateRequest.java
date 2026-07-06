package com.school.app.student;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;

import java.time.LocalDate;
import java.util.UUID;

public record StudentCreateRequest(
        @NotBlank String name,
        @NotBlank String rollNo,
        @NotBlank String studentClass,
        @NotBlank String section,
        @NotNull @Past LocalDate dob,
        UUID parentId
) {
}
