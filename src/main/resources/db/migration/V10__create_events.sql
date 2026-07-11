CREATE TABLE events (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title       VARCHAR(255) NOT NULL,
    description TEXT,
    event_date  DATE NOT NULL,
    location    VARCHAR(255),
    created_by  UUID NOT NULL REFERENCES users (id),
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE event_rsvps (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id     UUID NOT NULL REFERENCES events (id),
    user_id      UUID NOT NULL REFERENCES users (id),
    status       VARCHAR(20) NOT NULL CHECK (status IN ('GOING', 'MAYBE', 'NOT_GOING')),
    responded_at TIMESTAMP NOT NULL DEFAULT now(),

    CONSTRAINT uq_event_rsvp_once UNIQUE (event_id, user_id)
);

CREATE INDEX idx_events_event_date ON events (event_date);
CREATE INDEX idx_event_rsvps_event_id ON event_rsvps (event_id);
