CREATE TABLE scoring.consumed_messages (
    message_id UUID PRIMARY KEY,
    consumed_at TIMESTAMP WITH TIME ZONE NOT NULL
);
