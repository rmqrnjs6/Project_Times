-- Existing notes remain unchanged and keep NULL request IDs.
ALTER TABLE verification_notes ADD COLUMN request_id UUID;
CREATE UNIQUE INDEX verification_notes_request_unique
    ON verification_notes(task_id,request_id) WHERE request_id IS NOT NULL;
