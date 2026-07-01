CREATE SCHEMA IF NOT EXISTS verification;

CREATE TABLE verification.verification_results (
    id UUID PRIMARY KEY,
    simulation_id UUID NOT NULL,
    round_id UUID,
    vulnerability_id UUID NOT NULL,
    remediation_id UUID NOT NULL,
    target_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    evidence_summary VARCHAR(4000) NOT NULL,
    verified_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT verification_status_check CHECK (status IN ('PASSED', 'FAILED', 'INCONCLUSIVE'))
);

CREATE INDEX verification_simulation_time_idx
    ON verification.verification_results (simulation_id, verified_at, id);
CREATE INDEX verification_remediation_idx
    ON verification.verification_results (remediation_id);
CREATE INDEX verification_vulnerability_idx
    ON verification.verification_results (vulnerability_id);
