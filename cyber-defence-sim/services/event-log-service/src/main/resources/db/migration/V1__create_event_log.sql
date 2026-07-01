CREATE TABLE events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(200) NOT NULL,
    simulation_id UUID,
    round_id UUID,
    target_id UUID,
    producer_service VARCHAR(200) NOT NULL,
    correlation_id VARCHAR(128) NOT NULL,
    event_timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    payload_json TEXT NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_events_simulation_timestamp
    ON events (simulation_id, event_timestamp, event_id);

CREATE INDEX idx_events_correlation
    ON events (correlation_id);
