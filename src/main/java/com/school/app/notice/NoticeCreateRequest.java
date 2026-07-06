package com.school.app.notice;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record NoticeCreateRequest(
        @NotBlank String title,
        String description,
        @NotNull TargetRole targetRole
) {
}
