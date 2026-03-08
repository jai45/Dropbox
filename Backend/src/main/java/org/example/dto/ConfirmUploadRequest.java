package org.example.dto;

import lombok.Data;

/**
 * Sent by the client after it has finished uploading directly to R2
 * via the presigned PUT URL, to mark the file as READY.
 */
@Data
public class ConfirmUploadRequest {
    /**
     * SHA-256 hash of the uploaded file. Stamps content_hash for future deduplication.
     * Optional if it was already provided in the PresignRequest.
     */
    private String contentHash;
}
