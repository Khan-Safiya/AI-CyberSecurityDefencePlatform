CREATE FUNCTION reject_score_event_mutation()
RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'score events are append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER score_events_reject_update_or_delete
BEFORE UPDATE OR DELETE ON score_events
FOR EACH ROW EXECUTE FUNCTION reject_score_event_mutation();
