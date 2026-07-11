package com.school.app.transport;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bus_routes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusRoute {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String description;

    /** Bearer credential the bus's GPS device/app presents when pushing a location update — not a JWT. */
    @Column(name = "location_token", nullable = false, unique = true)
    private String locationToken;

    @Column(name = "current_lat")
    private Double currentLat;

    @Column(name = "current_lng")
    private Double currentLng;

    @Column(name = "location_updated_at")
    private Instant locationUpdatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (locationToken == null) {
            locationToken = UUID.randomUUID().toString();
        }
    }
}
