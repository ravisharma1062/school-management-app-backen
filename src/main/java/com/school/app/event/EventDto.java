package com.school.app.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record EventDto(
        UUID id,
        String title,
        String description,
        LocalDate eventDate,
        String location,
        UUID createdBy,
        Instant createdAt,
        RsvpStatus myRsvpStatus
) {
}
