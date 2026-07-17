package com.school.app.platform;

import com.school.app.common.exception.ResourceNotFoundException;
import com.school.app.common.notification.NotificationLogRepository;
import com.school.app.school.School;
import com.school.app.school.SchoolRepository;
import com.school.app.school.SchoolStatus;
import com.school.app.student.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformSchoolServiceTest {

    @Mock
    private SchoolRepository schoolRepository;
    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private EntitlementRepository entitlementRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private NotificationLogRepository notificationLogRepository;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private PlatformSchoolService platformSchoolService;

    private School school;
    private PlatformUser actor;

    @BeforeEach
    void setUp() {
        school = School.builder().id(UUID.randomUUID()).name("Springfield High").slug("springfield-high")
                .status(SchoolStatus.PAST_DUE).createdAt(Instant.now()).build();
        actor = PlatformUser.builder().id(UUID.randomUUID()).email("operator@school.app")
                .platformRole(PlatformRole.PLATFORM_ADMIN).build();
    }

    @Test
    void listMapsSchoolPageToAdminDtoPage() {
        Pageable pageable = PageRequest.of(0, 10);
        when(schoolRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(school), pageable, 1));

        Page<SchoolAdminDto> result = platformSchoolService.list(pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).id()).isEqualTo(school.getId());
        assertThat(result.getContent().get(0).slug()).isEqualTo("springfield-high");
    }

    @Test
    void getReturnsTheAdminDtoForAnExistingSchool() {
        when(schoolRepository.findById(school.getId())).thenReturn(Optional.of(school));

        SchoolAdminDto dto = platformSchoolService.get(school.getId());

        assertThat(dto.name()).isEqualTo("Springfield High");
    }

    @Test
    void getThrowsForAMissingSchool() {
        UUID missingId = UUID.randomUUID();
        when(schoolRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> platformSchoolService.get(missingId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateStatusMirrorsTheNewStatusOntoTheSubscriptionAndAudits() {
        Subscription subscription = Subscription.builder().id(UUID.randomUUID()).school(school)
                .status(SchoolStatus.PAST_DUE).build();
        when(schoolRepository.findById(school.getId())).thenReturn(Optional.of(school));
        when(subscriptionRepository.findBySchoolId(school.getId())).thenReturn(Optional.of(subscription));

        SchoolAdminDto dto = platformSchoolService.updateStatus(school.getId(), SchoolStatus.SUSPENDED, actor);

        assertThat(school.getStatus()).isEqualTo(SchoolStatus.SUSPENDED);
        assertThat(subscription.getStatus()).isEqualTo(SchoolStatus.SUSPENDED);
        verify(schoolRepository).save(school);
        verify(subscriptionRepository).save(subscription);
        verify(auditService).record(eq(actor), eq(AuditAction.SCHOOL_STATUS_CHANGED), eq(school.getId()),
                contains("from PAST_DUE to SUSPENDED"));
        assertThat(dto.status()).isEqualTo(SchoolStatus.SUSPENDED);
    }

    @Test
    void updateStatusToleratesASchoolWithNoSubscriptionRow() {
        when(schoolRepository.findById(school.getId())).thenReturn(Optional.of(school));
        when(subscriptionRepository.findBySchoolId(school.getId())).thenReturn(Optional.empty());

        platformSchoolService.updateStatus(school.getId(), SchoolStatus.CANCELLED, actor);

        assertThat(school.getStatus()).isEqualTo(SchoolStatus.CANCELLED);
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void getUsageAggregatesStudentCountAndNotificationCounts() {
        when(schoolRepository.findById(school.getId())).thenReturn(Optional.of(school));
        when(studentRepository.countActiveBySchoolIdBypassingTenantFilter(school.getId())).thenReturn(42L);
        Subscription subscription = Subscription.builder().id(UUID.randomUUID()).school(school)
                .status(SchoolStatus.ACTIVE).build();
        when(subscriptionRepository.findBySchoolId(school.getId())).thenReturn(Optional.of(subscription));
        when(entitlementRepository.findBySubscriptionIdAndFeatureKey(subscription.getId(), FeatureKey.MAX_STUDENTS))
                .thenReturn(Optional.of(Entitlement.builder().subscription(subscription)
                        .featureKey(FeatureKey.MAX_STUDENTS).enabled(true).limitValue(500).build()));
        when(notificationLogRepository.countSentByChannelSinceBypassingTenantFilter(eq(school.getId()), eq("EMAIL"), any(Instant.class)))
                .thenReturn(12L);
        when(notificationLogRepository.countSentByChannelSinceBypassingTenantFilter(eq(school.getId()), eq("SMS"), any(Instant.class)))
                .thenReturn(3L);

        SchoolUsageDto usage = platformSchoolService.getUsage(school.getId());

        assertThat(usage.activeStudentCount()).isEqualTo(42);
        assertThat(usage.maxStudentsLimit()).isEqualTo(500);
        assertThat(usage.emailsSentThisMonth()).isEqualTo(12);
        assertThat(usage.smsSentThisMonth()).isEqualTo(3);
    }

    @Test
    void getUsageThrowsForAMissingSchoolBeforeQueryingUsage() {
        UUID missingId = UUID.randomUUID();
        when(schoolRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> platformSchoolService.getUsage(missingId))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(studentRepository, never()).countActiveBySchoolIdBypassingTenantFilter(any());
    }
}
