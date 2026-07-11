CREATE TABLE leave_requests (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    requester_id UUID NOT NULL REFERENCES users (id),
    type         VARCHAR(20) NOT NULL CHECK (type IN ('SICK', 'CASUAL', 'OTHER')),
    from_date    DATE NOT NULL,
    to_date      DATE NOT NULL,
    reason       TEXT,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    reviewed_by  UUID REFERENCES users (id),
    created_at   TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_leave_requests_requester_id ON leave_requests (requester_id);
CREATE INDEX idx_leave_requests_status ON leave_requests (status);
