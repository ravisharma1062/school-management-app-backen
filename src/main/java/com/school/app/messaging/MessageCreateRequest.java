package com.school.app.messaging;

import jakarta.validation.constraints.NotBlank;

public record MessageCreateRequest(
        @NotBlank String body
) {
}
