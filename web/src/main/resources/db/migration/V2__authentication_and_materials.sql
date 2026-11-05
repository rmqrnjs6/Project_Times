CREATE TABLE security_users (
    username VARCHAR(64) PRIMARY KEY,
    password VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE security_authorities (
    username VARCHAR(64) NOT NULL REFERENCES security_users(username),
    authority VARCHAR(32) NOT NULL,
    PRIMARY KEY(username, authority)
);
ALTER TABLE diagnostic_checks ADD COLUMN owner_key VARCHAR(64) NOT NULL DEFAULT 'legacy-unassigned';
ALTER TABLE calculation_runs ADD COLUMN owner_key VARCHAR(64) NOT NULL DEFAULT 'legacy-unassigned';
CREATE INDEX diagnostic_checks_owner_created ON diagnostic_checks(owner_key, created_at DESC);
CREATE TABLE login_attempts (
    remote_key VARCHAR(64) PRIMARY KEY,
    window_started TIMESTAMPTZ NOT NULL,
    attempts INTEGER NOT NULL
);
CREATE TABLE security_events (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(32) NOT NULL,
    actor VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE external_materials (
    id UUID PRIMARY KEY,
    owner_key VARCHAR(64) NOT NULL,
    source_url VARCHAR(2048) NOT NULL,
    raw_text TEXT NOT NULL,
    sha256 CHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL CHECK (status = 'quarantined'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
