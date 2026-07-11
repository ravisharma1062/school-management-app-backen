package com.school.app.transport;

import java.util.UUID;

public record BusStopDto(
        UUID id,
        String name,
        int stopOrder,
        double latitude,
        double longitude
) {
}
