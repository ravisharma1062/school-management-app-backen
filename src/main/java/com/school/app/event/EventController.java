package com.school.app.event;

import com.school.app.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
@Tag(name = "Events")
public class EventController {

    private final EventService eventService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a school event")
    public EventDto create(@Valid @RequestBody EventCreateRequest request, @AuthenticationPrincipal User currentUser) {
        return eventService.create(request, currentUser);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'PARENT')")
    @Operation(summary = "List upcoming events over the next N days, with the caller's own RSVP status")
    public List<EventDto> list(
            @RequestParam(required = false, defaultValue = "30") int range,
            @AuthenticationPrincipal User currentUser) {
        return eventService.getInRange(range, currentUser);
    }

    @PostMapping("/{id}/rsvp")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'PARENT')")
    @Operation(summary = "Submit or update the caller's RSVP for an event")
    public EventRsvpDto rsvp(
            @PathVariable UUID id, @Valid @RequestBody EventRsvpRequest request, @AuthenticationPrincipal User currentUser) {
        return eventService.submitRsvp(id, request, currentUser);
    }

    @GetMapping("/{id}/rsvps")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List all RSVPs for an event")
    public List<EventRsvpDto> rsvps(@PathVariable UUID id) {
        return eventService.getRsvps(id);
    }
}
