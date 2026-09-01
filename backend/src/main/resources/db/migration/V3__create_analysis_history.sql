CREATE TABLE analysis_history (
    analysis_id UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES customers(customer_id),
    operator_id VARCHAR(128) NOT NULL CHECK (length(trim(operator_id)) > 0),
    generated_at TIMESTAMPTZ NOT NULL,
    risk_level VARCHAR(16) NOT NULL CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH')),
    findings_summary TEXT NOT NULL CHECK (length(trim(findings_summary)) > 0),
    recommendations JSONB NOT NULL CHECK (jsonb_typeof(recommendations) = 'array' AND jsonb_array_length(recommendations) > 0),
    evidence_provenance JSONB NOT NULL CHECK (jsonb_typeof(evidence_provenance) = 'array' AND jsonb_array_length(evidence_provenance) > 0)
);

CREATE INDEX analysis_history_customer_generated_idx
    ON analysis_history(customer_id, generated_at DESC, analysis_id DESC);
