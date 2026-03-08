package org.example.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class PresignRequest {
    private UUID ownerId;
    private String originalName;
    private String contentType;
    private Long sizeBytes;
    private String status;
    /** Optional SHA-256 (or any) hash of the file content for server-side deduplication. */
    private String contentHash;
}
