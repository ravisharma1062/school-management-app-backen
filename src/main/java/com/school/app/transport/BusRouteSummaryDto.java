package com.school.app.transport;

import java.util.UUID;

public record BusRouteSummaryDto(
        UUID id,
        String name,
        String description,
        int stopCount
) {
}
