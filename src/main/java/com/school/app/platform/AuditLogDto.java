package com.school.app.platform;

import java.time.Instant;
import java.util.UUID;

public record AuditLogDto(
        UUID id,
        String actorEmail,
        AuditAction action,
        UUID targetSchoolId,
        String summary,
        Instant createdAt
) {
}
