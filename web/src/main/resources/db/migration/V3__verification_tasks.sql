CREATE TABLE verification_tasks (
    id UUID PRIMARY KEY,
    owner_key VARCHAR(64) NOT NULL REFERENCES security_users(username),
    question VARCHAR(2000) NOT NULL CHECK (length(trim(question)) > 0),
    evidence_requirement VARCHAR(32) NOT NULL CHECK (evidence_requirement IN ('PRIMARY_REQUIRED','CROSS_CHECK_REQUIRED')),
    status VARCHAR(16) NOT NULL DEFAULT 'QUEUED'
        CHECK (status IN ('QUEUED','RUNNING','SUCCEEDED','FAILED','CANCELLED')),
    version BIGINT NOT NULL DEFAULT 0,
    lease_token UUID,
    lease_until TIMESTAMPTZ,
    result JSONB,
    failure_code VARCHAR(32),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK ((status = 'RUNNING') = (lease_token IS NOT NULL AND lease_until IS NOT NULL)),
    CHECK ((status = 'SUCCEEDED') = (result IS NOT NULL)),
    CHECK ((status = 'FAILED') = (failure_code IS NOT NULL))
);
CREATE INDEX verification_tasks_owner_created ON verification_tasks(owner_key,created_at DESC,id);
CREATE TABLE verification_task_events (
    task_id UUID NOT NULL REFERENCES verification_tasks(id),
    version BIGINT NOT NULL,
    from_status VARCHAR(16),
    to_status VARCHAR(16) NOT NULL,
    actor VARCHAR(80) NOT NULL,
    reason VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(task_id,version)
);
