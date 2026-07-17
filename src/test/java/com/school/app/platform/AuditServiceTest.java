package com.school.app.platform;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditService auditService;

    @Test
    void recordPersistsAnAuditLogWithAllFields() {
        PlatformUser actor = PlatformUser.builder()
                .id(UUID.randomUUID())
                .email("operator@school.app")
                .platformRole(PlatformRole.PLATFORM_ADMIN)
                .build();
        UUID targetSchoolId = UUID.randomUUID();

        auditService.record(actor, AuditAction.SCHOOL_STATUS_CHANGED, targetSchoolId, "'Springfield High' suspended");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getActor()).isEqualTo(actor);
        assertThat(saved.getAction()).isEqualTo(AuditAction.SCHOOL_STATUS_CHANGED);
        assertThat(saved.getTargetSchoolId()).isEqualTo(targetSchoolId);
        assertThat(saved.getSummary()).isEqualTo("'Springfield High' suspended");
    }

    @Test
    void recordAcceptsANullActorForSelfServiceActions() {
        auditService.record(null, AuditAction.TRIAL_SELF_PROVISIONED, null, "trial school self-provisioned");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getActor()).isNull();
        assertThat(captor.getValue().getTargetSchoolId()).isNull();
    }
}
