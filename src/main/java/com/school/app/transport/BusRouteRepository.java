package com.school.app.transport;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BusRouteRepository extends JpaRepository<BusRoute, UUID> {
}
