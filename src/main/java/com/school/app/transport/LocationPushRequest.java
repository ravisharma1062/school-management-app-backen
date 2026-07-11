package com.school.app.transport;

import jakarta.validation.constraints.NotNull;

public record LocationPushRequest(
        @NotNull Double latitude,
        @NotNull Double longitude
) {
}
