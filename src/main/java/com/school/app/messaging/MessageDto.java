package com.school.app.messaging;

import java.time.Instant;
import java.util.UUID;

public record MessageDto(
        UUID id,
        UUID conversationId,
        UUID senderId,
        String body,
        Instant sentAt
) {
}
