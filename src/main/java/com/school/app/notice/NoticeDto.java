package com.school.app.notice;

import java.time.Instant;
import java.util.UUID;

public record NoticeDto(
        UUID id,
        String title,
        String description,
        TargetRole targetRole,
        UUID createdBy,
        Instant createdAt,
        boolean active
) {
}
