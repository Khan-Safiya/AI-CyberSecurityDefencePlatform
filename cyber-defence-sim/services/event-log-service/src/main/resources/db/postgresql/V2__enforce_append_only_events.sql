CREATE FUNCTION reject_event_mutation()
RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'events are append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER events_reject_update_or_delete
BEFORE UPDATE OR DELETE ON events
FOR EACH ROW EXECUTE FUNCTION reject_event_mutation();
