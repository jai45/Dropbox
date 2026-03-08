-- V8: add part_count to multipart_upload_sessions for resume support
ALTER TABLE multipart_upload_sessions
    ADD COLUMN IF NOT EXISTS part_count INT NOT NULL DEFAULT 0;
