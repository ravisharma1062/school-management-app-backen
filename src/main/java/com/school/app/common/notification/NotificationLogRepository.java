package com.school.app.common.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.UUID;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {

    /**
     * Bypasses the {@code @TenantId} filter — for platform (MT-6c usage metering) callers, whose
     * token carries no tenant at all, mirroring {@code BusRouteRepository.findByIdBypassingTenantFilter}.
     */
    @Query(value = "SELECT COUNT(*) FROM notification_log "
            + "WHERE school_id = :schoolId AND channel = :channel AND status = 'SENT' AND created_at >= :since",
            nativeQuery = true)
    long countSentByChannelSinceBypassingTenantFilter(UUID schoolId, String channel, Instant since);
}
