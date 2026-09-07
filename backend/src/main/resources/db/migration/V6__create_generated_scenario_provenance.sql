-- Retains replay identity for opt-in demonstration scenarios without altering the four canonical fixtures.
CREATE TABLE generated_scenarios (
    customer_id UUID PRIMARY KEY REFERENCES customers(customer_id),
    seed BIGINT NOT NULL,
    family VARCHAR(32) NOT NULL CHECK (family IN ('ORDINARY_LOCAL', 'CROSS_BORDER_GROWTH', 'MIXED_RED_FLAGS')),
    generator_identity VARCHAR(64) NOT NULL CHECK (length(trim(generator_identity)) > 0),
    scenario_anchor_at TIMESTAMPTZ NOT NULL,
    activity_count INTEGER NOT NULL CHECK (activity_count > 0),
    risk_evidence_count INTEGER NOT NULL CHECK (risk_evidence_count >= 0),
    UNIQUE(seed, family, generator_identity)
);
