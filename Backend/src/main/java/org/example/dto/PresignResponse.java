package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
public class PresignResponse {
    private UUID fileId;
    private String objectKey;
    /** Presigned PUT URL for client to upload directly to R2. Null when deduplicated = true. */
    private String uploadUrl;
    /** True when the file already existed in storage — no upload is needed. */
    private boolean deduplicated;
}
