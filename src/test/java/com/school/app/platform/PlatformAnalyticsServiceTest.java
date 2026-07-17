package com.school.app.platform;

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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformAnalyticsServiceTest {

    @Mock
    private SchoolRepository schoolRepository;
    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private NotificationLogRepository notificationLogRepository;

    @InjectMocks
    private PlatformAnalyticsService platformAnalyticsService;

    private School activeSchool;
    private School trialSchool;
    private School suspendedSchool;

    @BeforeEach
    void setUp() {
        activeSchool = School.builder().id(UUID.randomUUID()).name("Active High").slug("active-high")
                .status(SchoolStatus.ACTIVE).build();
        trialSchool = School.builder().id(UUID.randomUUID()).name("Trial High").slug("trial-high")
                .status(SchoolStatus.TRIAL).build();
        suspendedSchool = School.builder().id(UUID.randomUUID()).name("Suspended High").slug("suspended-high")
                .status(SchoolStatus.SUSPENDED).build();
    }

    private SubscriptionPlan planOf(PlanCode code) {
        return SubscriptionPlan.builder().id(UUID.randomUUID()).code(code).name(code.name())
                .basePrice(BigDecimal.TEN).billingInterval(BillingInterval.MONTHLY).build();
    }

    @Test
    void aggregatesSchoolCountsByStatusAndByPlan() {
        when(schoolRepository.findAll()).thenReturn(List.of(activeSchool, trialSchool, suspendedSchool));
        when(subscriptionRepository.findBySchoolId(activeSchool.getId())).thenReturn(Optional.of(
                Subscription.builder().id(UUID.randomUUID()).school(activeSchool).plan(planOf(PlanCode.STANDARD))
                        .status(SchoolStatus.ACTIVE).build()));
        when(subscriptionRepository.findBySchoolId(trialSchool.getId())).thenReturn(Optional.of(
                Subscription.builder().id(UUID.randomUUID()).school(trialSchool).plan(planOf(PlanCode.BASIC))
                        .status(SchoolStatus.TRIAL).build()));
        // Suspended school has no subscription row at all — must be excluded from byPlan, not NPE.
        when(subscriptionRepository.findBySchoolId(suspendedSchool.getId())).thenReturn(Optional.empty());
        when(studentRepository.countActiveBySchoolIdBypassingTenantFilter(any())).thenReturn(0L);
        when(notificationLogRepository.countSentByChannelSinceBypassingTenantFilter(any(), anyString(), any(Instant.class)))
                .thenReturn(0L);

        PlatformAnalyticsDto dto = platformAnalyticsService.get();

        assertThat(dto.totalSchools()).isEqualTo(3);
        assertThat(dto.schoolsByStatus())
                .containsEntry(SchoolStatus.ACTIVE, 1L)
                .containsEntry(SchoolStatus.TRIAL, 1L)
                .containsEntry(SchoolStatus.SUSPENDED, 1L);
        assertThat(dto.schoolsByPlan())
                .containsEntry(PlanCode.STANDARD, 1L)
                .containsEntry(PlanCode.BASIC, 1L)
                .doesNotContainKey(PlanCode.PREMIUM);
        assertThat(dto.schoolsByPlan().values().stream().mapToLong(Long::longValue).sum()).isEqualTo(2);
    }

    @Test
    void sumsUsageMeteringAcrossEverySchool() {
        when(schoolRepository.findAll()).thenReturn(List.of(activeSchool, trialSchool));
        when(subscriptionRepository.findBySchoolId(any())).thenReturn(Optional.empty());
        when(studentRepository.countActiveBySchoolIdBypassingTenantFilter(activeSchool.getId())).thenReturn(100L);
        when(studentRepository.countActiveBySchoolIdBypassingTenantFilter(trialSchool.getId())).thenReturn(30L);
        when(notificationLogRepository.countSentByChannelSinceBypassingTenantFilter(eq(activeSchool.getId()), eq("EMAIL"), any(Instant.class)))
                .thenReturn(50L);
        when(notificationLogRepository.countSentByChannelSinceBypassingTenantFilter(eq(trialSchool.getId()), eq("EMAIL"), any(Instant.class)))
                .thenReturn(5L);
        when(notificationLogRepository.countSentByChannelSinceBypassingTenantFilter(eq(activeSchool.getId()), eq("SMS"), any(Instant.class)))
                .thenReturn(20L);
        when(notificationLogRepository.countSentByChannelSinceBypassingTenantFilter(eq(trialSchool.getId()), eq("SMS"), any(Instant.class)))
                .thenReturn(0L);

        PlatformAnalyticsDto dto = platformAnalyticsService.get();

        assertThat(dto.totalActiveStudents()).isEqualTo(130);
        assertThat(dto.totalEmailsSentThisMonth()).isEqualTo(55);
        assertThat(dto.totalSmsSentThisMonth()).isEqualTo(20);
    }

    @Test
    void handlesNoSchoolsAtAllWithoutError() {
        when(schoolRepository.findAll()).thenReturn(List.of());

        PlatformAnalyticsDto dto = platformAnalyticsService.get();

        assertThat(dto.totalSchools()).isZero();
        assertThat(dto.schoolsByStatus()).isEmpty();
        assertThat(dto.schoolsByPlan()).isEmpty();
        assertThat(dto.totalActiveStudents()).isZero();
        assertThat(dto.totalEmailsSentThisMonth()).isZero();
        assertThat(dto.totalSmsSentThisMonth()).isZero();
    }
}
