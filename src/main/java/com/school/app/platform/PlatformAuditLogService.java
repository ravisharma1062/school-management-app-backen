package com.school.app.platform;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlatformAuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Transactional(readOnly = true)
    public Page<AuditLogDto> list(Pageable pageable) {
        return auditLogRepository.findAllByOrderByCreatedAtDesc(pageable).map(log -> new AuditLogDto(
                log.getId(), log.getActor() != null ? log.getActor().getEmail() : "System (self-service)",
                log.getAction(), log.getTargetSchoolId(), log.getSummary(), log.getCreatedAt()));
    }
}
