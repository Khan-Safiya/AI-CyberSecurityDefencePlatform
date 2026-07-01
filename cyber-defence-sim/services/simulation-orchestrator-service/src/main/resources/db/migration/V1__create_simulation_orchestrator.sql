CREATE TABLE simulations (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    mode VARCHAR(50) NOT NULL,
    target_id UUID NOT NULL,
    status VARCHAR(50) NOT NULL,
    current_round INTEGER NOT NULL,
    max_rounds INTEGER NOT NULL,
    max_duration_minutes INTEGER NOT NULL,
    stop_when_no_new_findings_for_rounds INTEGER NOT NULL,
    red_team_score INTEGER NOT NULL,
    blue_team_score INTEGER NOT NULL,
    final_risk_score INTEGER NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ended_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE simulation_timeline (
    simulation_id UUID NOT NULL REFERENCES simulations(id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    timeline_entry VARCHAR(2000) NOT NULL,
    PRIMARY KEY (simulation_id, position)
);
