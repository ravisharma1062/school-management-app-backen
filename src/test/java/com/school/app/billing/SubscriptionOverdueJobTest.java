package com.school.app.billing;

import com.school.app.platform.AuditAction;
import com.school.app.platform.AuditService;
import com.school.app.platform.Subscription;
import com.school.app.platform.SubscriptionRepository;
import com.school.app.school.School;
import com.school.app.school.SchoolRepository;
import com.school.app.school.SchoolStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionOverdueJobTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private SchoolRepository schoolRepository;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private SubscriptionOverdueJob job;

    @Test
    void marksLapsedActiveSubscriptionsPastDueAndAuditsEachOne() {
        School school = School.builder()
                .id(UUID.randomUUID())
                .name("Springfield High")
                .slug("springfield-high")
                .status(SchoolStatus.ACTIVE)
                .build();
        Subscription subscription = Subscription.builder()
                .id(UUID.randomUUID())
                .school(school)
                .status(SchoolStatus.ACTIVE)
                .currentPeriodEnd(Instant.now().minus(3, ChronoUnit.DAYS))
                .build();
        when(subscriptionRepository.findByStatusAndCurrentPeriodEndBefore(eq(SchoolStatus.ACTIVE), any(Instant.class)))
                .thenReturn(List.of(subscription));

        job.markOverdueSubscriptionsPastDue();

        assertThat(school.getStatus()).isEqualTo(SchoolStatus.PAST_DUE);
        assertThat(subscription.getStatus()).isEqualTo(SchoolStatus.PAST_DUE);
        verify(schoolRepository).save(school);
        verify(subscriptionRepository).save(subscription);
        // No human actor — this is a scheduled job.
        verify(auditService).record(isNull(), eq(AuditAction.SUBSCRIPTION_MARKED_PAST_DUE), eq(school.getId()),
                contains("Springfield High"));
    }

    @Test
    void queriesOnlyActiveSubscriptionsSoPastDueNeverEscalatesFurther() {
        when(subscriptionRepository.findByStatusAndCurrentPeriodEndBefore(eq(SchoolStatus.ACTIVE), any(Instant.class)))
                .thenReturn(List.of());

        job.markOverdueSubscriptionsPastDue();

        // The one and only automatic transition is ACTIVE -> PAST_DUE; SUSPENDED requires an operator.
        verify(subscriptionRepository).findByStatusAndCurrentPeriodEndBefore(eq(SchoolStatus.ACTIVE), any(Instant.class));
        verify(subscriptionRepository, never()).save(any());
        verifyNoInteractions(schoolRepository, auditService);
    }
}
