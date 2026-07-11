package com.school.app.transport;

import java.time.Instant;

public record BusLocationDto(
        Double latitude,
        Double longitude,
        Instant updatedAt
) {
}
