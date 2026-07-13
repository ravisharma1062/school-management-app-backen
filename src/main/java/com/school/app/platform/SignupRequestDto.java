package com.school.app.platform;

import java.time.Instant;
import java.util.UUID;

public record SignupRequestDto(
        UUID id,
        String schoolName,
        String contactName,
        String contactEmail,
        String contactPhone,
        PlanCode desiredPlan,
        boolean wantsEmail,
        boolean wantsSms,
        SignupRequestStatus status,
        Instant createdAt
) {
    static SignupRequestDto from(SignupRequest r) {
        return new SignupRequestDto(
                r.getId(), r.getSchoolName(), r.getContactName(), r.getContactEmail(), r.getContactPhone(),
                r.getDesiredPlan(), r.isWantsEmail(), r.isWantsSms(), r.getStatus(), r.getCreatedAt());
    }
}
