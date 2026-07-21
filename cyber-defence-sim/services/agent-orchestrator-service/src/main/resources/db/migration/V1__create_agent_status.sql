CREATE TABLE agent_status (
    id UUID PRIMARY KEY,
    simulation_id UUID NOT NULL,
    team VARCHAR(20) NOT NULL,
    agent_name VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX idx_agent_status_simulation_team
    ON agent_status (simulation_id, team);
