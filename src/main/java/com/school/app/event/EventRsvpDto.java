package com.school.app.event;

import java.time.Instant;
import java.util.UUID;

public record EventRsvpDto(
        UUID id,
        UUID eventId,
        UUID userId,
        String userName,
        RsvpStatus status,
        Instant respondedAt
) {
}
