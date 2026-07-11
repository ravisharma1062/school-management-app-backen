CREATE TABLE bus_routes (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                 VARCHAR(255) NOT NULL,
    description          TEXT,
    location_token       VARCHAR(64) NOT NULL UNIQUE,
    current_lat          DOUBLE PRECISION,
    current_lng          DOUBLE PRECISION,
    location_updated_at  TIMESTAMP,
    created_at           TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE bus_stops (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    route_id    UUID NOT NULL REFERENCES bus_routes (id),
    name        VARCHAR(255) NOT NULL,
    stop_order  INT NOT NULL,
    latitude    DOUBLE PRECISION NOT NULL,
    longitude   DOUBLE PRECISION NOT NULL,

    CONSTRAINT uq_bus_stop_order UNIQUE (route_id, stop_order)
);

CREATE TABLE student_transport (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id  UUID NOT NULL UNIQUE REFERENCES students (id),
    route_id    UUID NOT NULL REFERENCES bus_routes (id),
    stop_id     UUID NOT NULL REFERENCES bus_stops (id)
);

CREATE INDEX idx_bus_stops_route_id ON bus_stops (route_id);
CREATE INDEX idx_student_transport_route_id ON student_transport (route_id);
