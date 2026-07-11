package com.school.app.event;

import jakarta.validation.constraints.NotNull;

public record EventRsvpRequest(
        @NotNull RsvpStatus status
) {
}
