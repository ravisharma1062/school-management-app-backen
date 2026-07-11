package com.school.app.transport;

import java.util.UUID;

public record StudentTransportDto(
        UUID studentId,
        UUID routeId,
        String routeName,
        UUID stopId,
        String stopName,
        double stopLatitude,
        double stopLongitude
) {
}
