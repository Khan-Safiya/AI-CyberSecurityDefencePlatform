CREATE SCHEMA IF NOT EXISTS scoring;

CREATE TABLE scoring.score_events (
    id UUID PRIMARY KEY,
    simulation_id UUID NOT NULL,
    round_id UUID,
    source_event_id UUID NOT NULL,
    agent_id UUID,
    score_type VARCHAR(60) NOT NULL,
    team VARCHAR(10) NOT NULL,
    points INTEGER NOT NULL,
    reason VARCHAR(500) NOT NULL,
    agent_blocked BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT score_event_source_unique UNIQUE (simulation_id, source_event_id),
    CONSTRAINT score_team_check CHECK (team IN ('RED', 'BLUE')),
    CONSTRAINT score_type_check CHECK (score_type IN (
        'RED_LOW_FINDING', 'RED_MEDIUM_FINDING', 'RED_HIGH_FINDING', 'RED_CRITICAL_FINDING',
        'RED_DUPLICATE_FINDING', 'RED_UNSAFE_ACTION', 'BLUE_VALID_DETECTION', 'BLUE_CORRECT_TRIAGE',
        'BLUE_VALID_REMEDIATION_PROPOSAL', 'BLUE_PATCH_APPLIED', 'BLUE_FIX_VERIFIED',
        'BLUE_FALSE_POSITIVE', 'BLUE_FAILED_PATCH'
    ))
);

CREATE INDEX score_events_simulation_time_idx
    ON scoring.score_events (simulation_id, created_at, id);
CREATE INDEX score_events_blocked_agent_idx
    ON scoring.score_events (simulation_id, agent_id, agent_blocked);
