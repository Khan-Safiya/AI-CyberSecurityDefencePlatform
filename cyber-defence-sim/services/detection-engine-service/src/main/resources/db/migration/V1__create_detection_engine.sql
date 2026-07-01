CREATE SCHEMA IF NOT EXISTS detection_engine;

CREATE TABLE detection_engine.detection_rules (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    event_pattern VARCHAR(2000) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT detection_rule_severity_check CHECK (severity IN ('INFO', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

CREATE TABLE detection_engine.detection_events (
    id UUID PRIMARY KEY,
    simulation_id UUID NOT NULL,
    round_id UUID,
    target_id UUID NOT NULL,
    source VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    message VARCHAR(2000) NOT NULL,
    related_action_id UUID,
    related_vulnerability_id UUID,
    metadata VARCHAR(8000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT detection_event_severity_check CHECK (severity IN ('INFO', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

CREATE INDEX detection_events_simulation_created_idx
    ON detection_engine.detection_events (simulation_id, created_at, id);
CREATE INDEX detection_events_target_created_idx
    ON detection_engine.detection_events (target_id, created_at, id);
CREATE INDEX detection_events_vulnerability_idx
    ON detection_engine.detection_events (related_vulnerability_id);

INSERT INTO detection_engine.detection_rules
    (id, name, description, event_pattern, severity, enabled, created_at, updated_at)
VALUES
    ('00000000-0000-0000-0000-000000000601', 'Repeated login testing', 'Observes repeated authorized login checks.', 'action.type=AUTHENTICATION_TEST count>=3', 'MEDIUM', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000602', 'Access-control test observed', 'Observes an authorized access-control check.', 'action.type=ACCESS_CONTROL_TEST', 'MEDIUM', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000603', 'Input-validation check observed', 'Observes harmless input-validation test values.', 'action.type=INPUT_VALIDATION_TEST', 'LOW', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000604', 'Debug endpoint accessed', 'Observes access to an approved debug endpoint.', 'action.type=CONFIG_EXPOSURE_TEST', 'HIGH', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000605', 'Dependency-risk check performed', 'Observes a software dependency inventory check.', 'action.type=DEPENDENCY_RISK_TEST', 'INFO', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000606', 'Unsafe action blocked', 'Observes the policy engine blocking an out-of-scope action.', 'policy.decision=DENY', 'CRITICAL', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO detection_engine.detection_events
    (id, simulation_id, round_id, target_id, source, event_type, severity, message, related_action_id, related_vulnerability_id, metadata, created_at)
VALUES
    ('00000000-0000-0000-0000-000000000701', '00000000-0000-0000-0000-000000000201', NULL, '00000000-0000-0000-0000-000000000101', 'RED_TEAM_ACTION', 'detection.created', 'MEDIUM', 'Authorized repeated login testing was observed.', NULL, '00000000-0000-0000-0000-000000000501', '{"ruleId":"00000000-0000-0000-0000-000000000601"}', CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000702', '00000000-0000-0000-0000-000000000201', NULL, '00000000-0000-0000-0000-000000000101', 'RED_TEAM_ACTION', 'detection.created', 'MEDIUM', 'An authorized access-control test was observed.', NULL, '00000000-0000-0000-0000-000000000502', '{"ruleId":"00000000-0000-0000-0000-000000000602"}', CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000703', '00000000-0000-0000-0000-000000000201', NULL, '00000000-0000-0000-0000-000000000101', 'RED_TEAM_ACTION', 'detection.created', 'LOW', 'A harmless input-validation check was observed.', NULL, '00000000-0000-0000-0000-000000000505', '{"ruleId":"00000000-0000-0000-0000-000000000603"}', CURRENT_TIMESTAMP);
