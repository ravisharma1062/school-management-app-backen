package com.school.app.transport;

import com.school.app.platform.FeatureKey;
import com.school.app.platform.RequiresEntitlement;
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
@RequestMapping("/api/v1/transport")
@RequiredArgsConstructor
@Tag(name = "Transport")
public class TransportController {

    private final TransportService transportService;

    @PostMapping("/routes")
    @PreAuthorize("hasRole('ADMIN')")
    @RequiresEntitlement(FeatureKey.TRANSPORT_TRACKING)
    @Operation(summary = "Create a bus route with its stops")
    public BusRouteAdminDto createRoute(@Valid @RequestBody BusRouteCreateRequest request) {
        return transportService.createRoute(request);
    }

    @GetMapping("/routes")
    @PreAuthorize("hasRole('ADMIN')")
    @RequiresEntitlement(FeatureKey.TRANSPORT_TRACKING)
    @Operation(summary = "List all bus routes")
    public List<BusRouteSummaryDto> listRoutes() {
        return transportService.listRoutes();
    }

    @GetMapping("/routes/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @RequiresEntitlement(FeatureKey.TRANSPORT_TRACKING)
    @Operation(summary = "Get a bus route's full detail, including its device location-token")
    public BusRouteAdminDto getRoute(@PathVariable UUID id) {
        return transportService.getRoute(id);
    }

    @PostMapping("/routes/{id}/location")
    @Operation(summary = "Device-authenticated GPS location push (no JWT — authenticated by the route's location token)")
    public void pushLocation(
            @PathVariable UUID id,
            @RequestHeader("X-Location-Token") String token,
            @Valid @RequestBody LocationPushRequest request) {
        transportService.pushLocation(id, token, request);
    }

    @GetMapping("/routes/{id}/location/latest")
    @PreAuthorize("hasAnyRole('ADMIN', 'PARENT')")
    @Operation(summary = "Latest known location for a bus route (parents must have a child assigned to it)")
    public BusLocationDto getLatestLocation(@PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
        return transportService.getLatestLocation(id, currentUser);
    }

    @PutMapping("/students/{studentId}")
    @PreAuthorize("hasRole('ADMIN')")
    @RequiresEntitlement(FeatureKey.TRANSPORT_TRACKING)
    @Operation(summary = "Assign (or reassign) a student to a bus route and stop")
    public StudentTransportDto assignStudent(
            @PathVariable UUID studentId, @Valid @RequestBody StudentTransportAssignRequest request) {
        return transportService.assignStudent(studentId, request);
    }

    @GetMapping("/students/{studentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PARENT')")
    @Operation(summary = "Get a student's bus route/stop assignment")
    public StudentTransportDto getStudentAssignment(
            @PathVariable UUID studentId, @AuthenticationPrincipal User currentUser) {
        return transportService.getStudentAssignment(studentId, currentUser);
    }
}
