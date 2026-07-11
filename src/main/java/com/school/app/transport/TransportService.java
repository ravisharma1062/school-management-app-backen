package com.school.app.transport;

import com.school.app.common.exception.BadRequestException;
import com.school.app.common.exception.ResourceNotFoundException;
import com.school.app.student.Student;
import com.school.app.student.StudentRepository;
import com.school.app.user.Role;
import com.school.app.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransportService {

    private final BusRouteRepository busRouteRepository;
    private final BusStopRepository busStopRepository;
    private final StudentTransportRepository studentTransportRepository;
    private final StudentRepository studentRepository;
    private final TransportMapper transportMapper;

    public BusRouteAdminDto createRoute(BusRouteCreateRequest request) {
        BusRoute route = busRouteRepository.save(BusRoute.builder()
                .name(request.name())
                .description(request.description())
                .build());

        List<BusStop> stops = request.stops().stream()
                .map(s -> BusStop.builder()
                        .route(route)
                        .name(s.name())
                        .stopOrder(s.stopOrder())
                        .latitude(s.latitude())
                        .longitude(s.longitude())
                        .build())
                .toList();
        List<BusStop> saved = busStopRepository.saveAll(stops);

        return transportMapper.toAdminDto(route, saved);
    }

    public List<BusRouteSummaryDto> listRoutes() {
        return busRouteRepository.findAll().stream()
                .map(route -> transportMapper.toSummaryDto(route, busStopRepository.findByRouteIdOrderByStopOrderAsc(route.getId()).size()))
                .toList();
    }

    public BusRouteAdminDto getRoute(UUID routeId) {
        BusRoute route = requireRoute(routeId);
        List<BusStop> stops = busStopRepository.findByRouteIdOrderByStopOrderAsc(routeId);
        return transportMapper.toAdminDto(route, stops);
    }

    public void pushLocation(UUID routeId, String token, LocationPushRequest request) {
        BusRoute route = requireRoute(routeId);
        if (!constantTimeEquals(route.getLocationToken(), token)) {
            throw new AccessDeniedException("Invalid location token");
        }
        route.setCurrentLat(request.latitude());
        route.setCurrentLng(request.longitude());
        route.setLocationUpdatedAt(Instant.now());
        busRouteRepository.save(route);
    }

    public BusLocationDto getLatestLocation(UUID routeId, User currentUser) {
        BusRoute route = requireRoute(routeId);
        if (currentUser.getRole() == Role.PARENT) {
            requireParentHasChildOnRoute(routeId, currentUser.getId());
        }
        return transportMapper.toLocationDto(route);
    }

    public StudentTransportDto assignStudent(UUID studentId, StudentTransportAssignRequest request) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student with id " + studentId + " not found"));
        BusRoute route = requireRoute(request.routeId());
        BusStop stop = busStopRepository.findById(request.stopId())
                .orElseThrow(() -> new ResourceNotFoundException("Bus stop with id " + request.stopId() + " not found"));

        if (!stop.getRoute().getId().equals(route.getId())) {
            throw new BadRequestException("That stop does not belong to the given route");
        }

        StudentTransport assignment = studentTransportRepository.findByStudentId(studentId)
                .orElseGet(() -> StudentTransport.builder().student(student).build());
        assignment.setStudent(student);
        assignment.setRoute(route);
        assignment.setStop(stop);

        StudentTransport saved = studentTransportRepository.save(assignment);
        saved.setStudent(student);
        saved.setRoute(route);
        saved.setStop(stop);
        return transportMapper.toDto(saved);
    }

    public StudentTransportDto getStudentAssignment(UUID studentId, User currentUser) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student with id " + studentId + " not found"));

        if (currentUser.getRole() == Role.PARENT
                && (student.getParent() == null || !student.getParent().getId().equals(currentUser.getId()))) {
            throw new AccessDeniedException("Parents may only view their own child's transport assignment");
        }

        StudentTransport assignment = studentTransportRepository.findByStudentIdFetchRouteAndStop(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student " + studentId + " has no transport assignment"));
        return transportMapper.toDto(assignment);
    }

    private void requireParentHasChildOnRoute(UUID routeId, UUID parentId) {
        boolean hasChildOnRoute = studentRepository.findByParentIdAndActiveTrue(parentId).stream()
                .anyMatch(child -> studentTransportRepository.findByStudentId(child.getId())
                        .map(st -> st.getRoute().getId().equals(routeId))
                        .orElse(false));
        if (!hasChildOnRoute) {
            throw new AccessDeniedException("You have no child assigned to this route");
        }
    }

    private BusRoute requireRoute(UUID routeId) {
        return busRouteRepository.findById(routeId)
                .orElseThrow(() -> new ResourceNotFoundException("Bus route with id " + routeId + " not found"));
    }

    private boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }
}
