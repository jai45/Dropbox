-- V6: add content_hash for client-side deduplication
ALTER TABLE files ADD COLUMN content_hash TEXT;

-- Partial index for fast hash lookup (only READY, non-deleted files)
CREATE INDEX idx_files_content_hash ON files(content_hash)
    WHERE is_deleted = 'N' AND status = 'READY';
