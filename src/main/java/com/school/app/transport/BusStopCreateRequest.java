package com.school.app.transport;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BusStopCreateRequest(
        @NotBlank String name,
        @NotNull Integer stopOrder,
        @NotNull Double latitude,
        @NotNull Double longitude
) {
}
