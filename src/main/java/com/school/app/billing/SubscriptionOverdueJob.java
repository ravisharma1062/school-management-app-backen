package com.school.app.billing;

import com.school.app.platform.AuditAction;
import com.school.app.platform.AuditService;
import com.school.app.platform.Subscription;
import com.school.app.platform.SubscriptionRepository;
import com.school.app.school.School;
import com.school.app.school.SchoolRepository;
import com.school.app.school.SchoolStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * MT-5 (manual/offline billing) has no payment gateway pushing status transitions via webhook, so
 * this is the one automatic transition that exists: {@code ACTIVE -> PAST_DUE} once a billing
 * period lapses with no verified payment. By explicit product decision it goes NO further —
 * {@code PAST_DUE -> SUSPENDED} always requires an operator's manual call via
 * {@code PlatformSchoolService.updateStatus}, since a school paying by DD/cheque/NEFT can
 * legitimately run a few days late without having actually churned. This just makes the overdue
 * state visible (reusing MT-2's existing {@code PAST_DUE} banner/UI) — it never locks anyone out.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionOverdueJob {

    private final SubscriptionRepository subscriptionRepository;
    private final SchoolRepository schoolRepository;
    private final AuditService auditService;

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void markOverdueSubscriptionsPastDue() {
        List<Subscription> overdue = subscriptionRepository.findByStatusAndCurrentPeriodEndBefore(SchoolStatus.ACTIVE, Instant.now());
        for (Subscription subscription : overdue) {
            School school = subscription.getSchool();
            school.setStatus(SchoolStatus.PAST_DUE);
            schoolRepository.save(school);
            subscription.setStatus(SchoolStatus.PAST_DUE);
            subscriptionRepository.save(subscription);

            auditService.record(null, AuditAction.SUBSCRIPTION_MARKED_PAST_DUE, school.getId(),
                    "'" + school.getName() + "' marked PAST_DUE — billing period ended " + subscription.getCurrentPeriodEnd()
                            + " with no verified payment");
            log.info("Marked school {} PAST_DUE (period ended {})", school.getId(), subscription.getCurrentPeriodEnd());
        }
    }
}
