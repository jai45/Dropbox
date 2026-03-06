package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
public class InitiateMultipartResponse {
    private UUID uploadSessionId; // internal DB session ID
    private UUID fileId;          // pre-allocated FileMetadata ID
    private String objectKey;     // the R2 object key
    private String uploadId;      // the R2 multipart upload ID
    /** Presigned PUT URLs for every part, ordered by partNumber (1-based). */
    private List<PresignPartResponse> parts;
}
