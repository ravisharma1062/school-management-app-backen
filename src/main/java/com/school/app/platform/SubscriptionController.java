package com.school.app.platform;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/subscription")
@RequiredArgsConstructor
@Tag(name = "Subscription")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Current plan, status, and resolved entitlement map for the caller's school")
    public SubscriptionDto getCurrent() {
        return subscriptionService.getCurrent();
    }
}
