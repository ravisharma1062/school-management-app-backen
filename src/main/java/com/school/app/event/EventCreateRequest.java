package com.school.app.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record EventCreateRequest(
        @NotBlank String title,
        String description,
        @NotNull LocalDate eventDate,
        String location
) {
}
