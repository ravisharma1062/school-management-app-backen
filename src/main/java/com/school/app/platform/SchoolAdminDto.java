package com.school.app.platform;

import com.school.app.school.School;
import com.school.app.school.SchoolStatus;

import java.time.Instant;
import java.util.UUID;

public record SchoolAdminDto(
        UUID id,
        String name,
        String slug,
        SchoolStatus status,
        Instant createdAt
) {
    static SchoolAdminDto from(School s) {
        return new SchoolAdminDto(s.getId(), s.getName(), s.getSlug(), s.getStatus(), s.getCreatedAt());
    }
}
