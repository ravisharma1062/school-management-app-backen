package com.school.app.transport;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record StudentTransportAssignRequest(
        @NotNull UUID routeId,
        @NotNull UUID stopId
) {
}
