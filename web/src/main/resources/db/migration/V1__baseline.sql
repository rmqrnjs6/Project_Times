CREATE TABLE diagnostic_checks (
    id UUID PRIMARY KEY,
    question VARCHAR(2000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    kind VARCHAR(40) NOT NULL DEFAULT 'connectivity_only'
);
CREATE TABLE calculation_runs (
    id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    policy_version VARCHAR(100) NOT NULL,
    input JSONB NOT NULL,
    result JSONB NOT NULL
);
