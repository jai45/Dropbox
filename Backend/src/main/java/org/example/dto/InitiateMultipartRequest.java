package org.example.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class InitiateMultipartRequest {
    /** The authenticated user's ID (pulled from JWT in the service layer). */
    private UUID ownerId;
    private String originalName;
    private String contentType;
    private Long sizeBytes;
    /**
     * Total number of parts the client will upload.
     * The backend pre-generates a presigned URL for each part (1..partCount)
     * so the client can upload all chunks concurrently.
     * Each part must be ≥ 5 MB except the last one.
     */
    private int partCount;
    /** Optional SHA-256 (or any) hash of the full file content for server-side deduplication. */
    private String contentHash;
}
