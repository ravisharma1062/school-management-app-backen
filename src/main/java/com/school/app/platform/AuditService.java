package com.school.app.platform;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public void record(PlatformUser actor, AuditAction action, UUID targetSchoolId, String summary) {
        auditLogRepository.save(AuditLog.builder()
                .actor(actor)
                .action(action)
                .targetSchoolId(targetSchoolId)
                .summary(summary)
                .build());
    }
}
