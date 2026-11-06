CREATE TABLE verification_notes (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES verification_tasks(id),
    author VARCHAR(64) NOT NULL,
    source_url VARCHAR(2048) NOT NULL,
    original_text TEXT NOT NULL CHECK (length(original_text) BETWEEN 1 AND 4000),
    interpretation TEXT NOT NULL CHECK (length(interpretation) BETWEEN 1 AND 4000),
    stance VARCHAR(24) NOT NULL CHECK (stance IN ('SUPPORTS','CONTRADICTS','UNVERIFIED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX verification_notes_task_created ON verification_notes(task_id,created_at,id);
