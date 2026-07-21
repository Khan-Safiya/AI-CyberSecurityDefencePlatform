ALTER TABLE simulation_rounds
    DROP CONSTRAINT simulation_round_status_check;

ALTER TABLE simulation_rounds
    ADD CONSTRAINT simulation_round_status_check CHECK (status IN (
        'CREATED',
        'RED_TEAM_RUNNING',
        'DETECTION_RUNNING',
        'BLUE_TEAM_RUNNING',
        'VERIFYING',
        'SCORING',
        'COMPLETED',
        'FAILED'
    ));
