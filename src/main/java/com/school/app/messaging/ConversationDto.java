package com.school.app.messaging;

import java.time.Instant;
import java.util.UUID;

public record ConversationDto(
        UUID id,
        UUID parentId,
        String parentName,
        UUID teacherId,
        String teacherName,
        Instant createdAt
) {
}
