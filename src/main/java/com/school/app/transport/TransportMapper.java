package com.school.app.transport;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TransportMapper {

    public BusStopDto toDto(BusStop stop) {
        return new BusStopDto(stop.getId(), stop.getName(), stop.getStopOrder(), stop.getLatitude(), stop.getLongitude());
    }

    public BusRouteAdminDto toAdminDto(BusRoute route, List<BusStop> stops) {
        return new BusRouteAdminDto(
                route.getId(),
                route.getName(),
                route.getDescription(),
                route.getLocationToken(),
                stops.stream().map(this::toDto).toList(),
                route.getCreatedAt());
    }

    public BusRouteSummaryDto toSummaryDto(BusRoute route, int stopCount) {
        return new BusRouteSummaryDto(route.getId(), route.getName(), route.getDescription(), stopCount);
    }

    public BusLocationDto toLocationDto(BusRoute route) {
        return new BusLocationDto(route.getCurrentLat(), route.getCurrentLng(), route.getLocationUpdatedAt());
    }

    public StudentTransportDto toDto(StudentTransport st) {
        return new StudentTransportDto(
                st.getStudent().getId(),
                st.getRoute().getId(),
                st.getRoute().getName(),
                st.getStop().getId(),
                st.getStop().getName(),
                st.getStop().getLatitude(),
                st.getStop().getLongitude());
    }
}
