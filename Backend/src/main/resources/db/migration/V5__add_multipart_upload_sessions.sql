-- V5: track in-progress multipart upload sessions
CREATE TABLE multipart_upload_sessions (
    id            UUID        PRIMARY KEY,
    file_id       UUID        NOT NULL REFERENCES files(id),
    owner_id      UUID        NOT NULL REFERENCES users(id),
    object_key    TEXT        NOT NULL,
    upload_id     TEXT        NOT NULL,    -- R2 / S3 multipart upload ID
    status        TEXT        NOT NULL DEFAULT 'IN_PROGRESS', -- IN_PROGRESS | COMPLETED | ABORTED
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
