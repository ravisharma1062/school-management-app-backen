package com.school.app.student;

import java.time.LocalDate;
import java.util.UUID;

public record StudentDto(
        UUID id,
        String name,
        String rollNo,
        String studentClass,
        String section,
        LocalDate dob,
        UUID parentId
) {
}
