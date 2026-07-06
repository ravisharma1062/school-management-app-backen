package com.school.app.student;

import jakarta.validation.constraints.Past;

import java.time.LocalDate;
import java.util.UUID;

public record StudentUpdateRequest(
        String name,
        String rollNo,
        String studentClass,
        String section,
        @Past LocalDate dob,
        UUID parentId
) {
}
