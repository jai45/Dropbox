-- V7: track individual part uploads for resumable multipart upload support
CREATE TABLE multipart_upload_parts (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id    UUID        NOT NULL REFERENCES multipart_upload_sessions(id) ON DELETE CASCADE,
    part_number   INT         NOT NULL,                              -- 1-based S3/R2 part number
    e_tag         TEXT,                                              -- ETag returned by R2 after successful PUT; NULL until confirmed
    size_bytes    BIGINT,                                            -- size of this part in bytes
    status        TEXT        NOT NULL DEFAULT 'PENDING',           -- PENDING | UPLOADED
    uploaded_at   TIMESTAMPTZ,                                       -- timestamp when the client confirmed the part
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_session_part UNIQUE (session_id, part_number)
);

CREATE INDEX idx_mup_session_status ON multipart_upload_parts(session_id, status);
