-- Adds the replaceable R4 policy-retrieval store; vector contents ground analysis but are not source truth.
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE policy_vector_store (
    id UUID PRIMARY KEY,
    content TEXT NOT NULL,
    metadata JSONB NOT NULL,
    embedding vector(384) NOT NULL
);

CREATE INDEX policy_vector_store_hnsw_idx
    ON policy_vector_store
    USING HNSW (embedding vector_cosine_ops);
