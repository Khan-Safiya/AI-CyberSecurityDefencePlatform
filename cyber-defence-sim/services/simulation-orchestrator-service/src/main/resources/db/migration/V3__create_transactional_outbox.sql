CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    event_type VARCHAR(200) NOT NULL,
    routing_key VARCHAR(200) NOT NULL,
    simulation_id UUID NOT NULL,
    round_id UUID,
    payload_json VARCHAR(8000) NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INTEGER NOT NULL,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE,
    last_error VARCHAR(500),
    CONSTRAINT outbox_status_check CHECK (status IN ('PENDING', 'FAILED', 'PUBLISHED')),
    CONSTRAINT outbox_attempts_check CHECK (attempts >= 0)
);

CREATE INDEX outbox_ready_idx
    ON outbox_events (status, next_attempt_at, created_at);
CREATE INDEX outbox_simulation_idx
    ON outbox_events (simulation_id, created_at);
