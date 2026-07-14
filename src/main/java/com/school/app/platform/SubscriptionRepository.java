package com.school.app.platform;

import com.school.app.school.SchoolStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findBySchoolId(UUID schoolId);

    /** MT-5's {@code SubscriptionOverdueJob} — subscriptions whose billing period lapsed unpaid. */
    List<Subscription> findByStatusAndCurrentPeriodEndBefore(SchoolStatus status, Instant currentPeriodEnd);
}
