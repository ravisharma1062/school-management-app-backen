package com.school.app.transport;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Full route detail including the device location-token — admin-only, never sent to parents. */
public record BusRouteAdminDto(
        UUID id,
        String name,
        String description,
        String locationToken,
        List<BusStopDto> stops,
        Instant createdAt
) {
}
