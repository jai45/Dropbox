package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
public class InitiateMultipartResponse {
    private UUID uploadSessionId; // internal DB session ID — null when deduplicated
    private UUID fileId;          // pre-allocated FileMetadata ID
    private String objectKey;     // the R2 object key
    private String uploadId;      // the R2 multipart upload ID — null when deduplicated
    /** Presigned PUT URLs for every part, ordered by partNumber (1-based). Null when deduplicated. */
    private List<PresignPartResponse> parts;
    /** True when the file already existed in storage — no upload is needed. */
    private boolean deduplicated;
}
