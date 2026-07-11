package com.school.app.event;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {

    List<Event> findByEventDateBetweenOrderByEventDateAsc(LocalDate from, LocalDate to);
}
