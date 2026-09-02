ALTER TABLE analysis_history
    ADD COLUMN detector_provenance JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN model_provenance JSONB NOT NULL DEFAULT '{"backendIdentity":"deterministic","modelIdentity":"r3-legacy-unversioned","metadata":{"migration":"V4"}}'::jsonb;
