package com.school.app.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRsvpRepository extends JpaRepository<EventRsvp, UUID> {

    Optional<EventRsvp> findByEventIdAndUserId(UUID eventId, UUID userId);

    @Query("select r from EventRsvp r join fetch r.user where r.event.id = :eventId")
    List<EventRsvp> findByEventIdFetchUser(@Param("eventId") UUID eventId);

    List<EventRsvp> findByEventIdInAndUserId(List<UUID> eventIds, UUID userId);
}
