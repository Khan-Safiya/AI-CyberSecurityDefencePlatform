CREATE TABLE consumed_messages (
    message_id UUID PRIMARY KEY,
    event_type VARCHAR(200) NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_consumed_messages_consumed_at
    ON consumed_messages (consumed_at);
