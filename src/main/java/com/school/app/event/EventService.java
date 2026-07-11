package com.school.app.event;

import com.school.app.common.exception.ResourceNotFoundException;
import com.school.app.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final EventRsvpRepository eventRsvpRepository;
    private final EventMapper eventMapper;

    public EventDto create(EventCreateRequest request, User currentUser) {
        Event event = Event.builder()
                .title(request.title())
                .description(request.description())
                .eventDate(request.eventDate())
                .location(request.location())
                .createdBy(currentUser)
                .build();

        return eventMapper.toDto(eventRepository.save(event), null);
    }

    public List<EventDto> getInRange(int rangeDays, User currentUser) {
        int days = rangeDays > 0 ? rangeDays : 30;
        LocalDate from = LocalDate.now();
        LocalDate to = from.plusDays(days - 1L);

        List<Event> events = eventRepository.findByEventDateBetweenOrderByEventDateAsc(from, to);
        List<UUID> eventIds = events.stream().map(Event::getId).toList();

        Map<UUID, RsvpStatus> myRsvpByEventId = eventRsvpRepository.findByEventIdInAndUserId(eventIds, currentUser.getId())
                .stream()
                .collect(Collectors.toMap(r -> r.getEvent().getId(), EventRsvp::getStatus));

        return events.stream()
                .map(event -> eventMapper.toDto(event, myRsvpByEventId.get(event.getId())))
                .toList();
    }

    public EventRsvpDto submitRsvp(UUID eventId, EventRsvpRequest request, User currentUser) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event with id " + eventId + " not found"));

        EventRsvp rsvp = eventRsvpRepository.findByEventIdAndUserId(eventId, currentUser.getId())
                .orElseGet(() -> EventRsvp.builder().event(event).user(currentUser).build());
        rsvp.setUser(currentUser);
        rsvp.setStatus(request.status());

        EventRsvp saved = eventRsvpRepository.save(rsvp);
        // save() on an existing (detached) rsvp merges into a newly re-fetched managed copy whose
        // `user` association is a fresh lazy proxy — reassign the already-hydrated currentUser here,
        // on the object actually passed to the mapper, so toDto() can safely read user.getName().
        saved.setUser(currentUser);
        return eventMapper.toDto(saved);
    }

    public List<EventRsvpDto> getRsvps(UUID eventId) {
        if (!eventRepository.existsById(eventId)) {
            throw new ResourceNotFoundException("Event with id " + eventId + " not found");
        }
        return eventRsvpRepository.findByEventIdFetchUser(eventId).stream()
                .map(eventMapper::toDto)
                .toList();
    }
}
