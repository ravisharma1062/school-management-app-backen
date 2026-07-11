package com.school.app.event;

import org.springframework.stereotype.Component;

@Component
public class EventMapper {

    public EventDto toDto(Event event, RsvpStatus myRsvpStatus) {
        return new EventDto(
                event.getId(),
                event.getTitle(),
                event.getDescription(),
                event.getEventDate(),
                event.getLocation(),
                event.getCreatedBy().getId(),
                event.getCreatedAt(),
                myRsvpStatus
        );
    }

    public EventRsvpDto toDto(EventRsvp rsvp) {
        return new EventRsvpDto(
                rsvp.getId(),
                rsvp.getEvent().getId(),
                rsvp.getUser().getId(),
                rsvp.getUser().getName(),
                rsvp.getStatus(),
                rsvp.getRespondedAt()
        );
    }
}
