ALTER TABLE simulations ADD COLUMN minimum_accepted_risk_level VARCHAR(20) NOT NULL DEFAULT 'LOW';
ALTER TABLE simulations ADD COLUMN retest_enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE simulations ADD COLUMN retest_delay_seconds INTEGER NOT NULL DEFAULT 10;
ALTER TABLE simulations ADD COLUMN stop_reason VARCHAR(100);

CREATE TABLE simulation_rounds (
    id UUID PRIMARY KEY,
    simulation_id UUID NOT NULL REFERENCES simulations(id) ON DELETE CASCADE,
    round_number INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ended_at TIMESTAMP WITH TIME ZONE,
    new_findings_count INTEGER NOT NULL,
    remediated_findings_count INTEGER NOT NULL,
    verified_fixes_count INTEGER NOT NULL,
    risk_score_before INTEGER NOT NULL,
    risk_score_after INTEGER NOT NULL,
    CONSTRAINT simulation_round_number_unique UNIQUE (simulation_id, round_number),
    CONSTRAINT simulation_round_status_check CHECK (status IN (
        'CREATED', 'RED_TEAM_RUNNING', 'BLUE_TEAM_RUNNING', 'VERIFYING', 'COMPLETED', 'FAILED'
    )),
    CONSTRAINT simulation_round_counts_check CHECK (
        new_findings_count >= 0 AND remediated_findings_count >= 0 AND verified_fixes_count >= 0
    ),
    CONSTRAINT simulation_round_risk_check CHECK (
        risk_score_before BETWEEN 0 AND 100 AND risk_score_after BETWEEN 0 AND 100
    )
);

CREATE INDEX simulation_rounds_simulation_number_idx
    ON simulation_rounds (simulation_id, round_number);
