package com.school.app.common.notification;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notification-preferences")
@RequiredArgsConstructor
@Tag(name = "Notification Preferences")
public class NotificationPreferenceController {

    private final NotificationPreferenceService preferenceService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List which channels are enabled for each notification event type")
    public List<NotificationPreferenceDto> list() {
        return preferenceService.list();
    }

    @PatchMapping("/{eventType}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Enable/disable SMS, email, and push for one event type")
    public NotificationPreferenceDto update(
            @PathVariable NotificationEventType eventType,
            @Valid @RequestBody NotificationPreferenceUpdateRequest request) {
        return preferenceService.update(eventType, request);
    }
}
