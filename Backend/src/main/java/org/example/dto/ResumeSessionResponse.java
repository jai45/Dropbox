package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * Response for GET /api/v1/multipart/resume?uploadSessionId=…
 *
 * Returns the session info plus fresh presigned URLs only for the parts that
 * are still PENDING so the client can continue uploading from where it left off.
 */
@Data
@AllArgsConstructor
public class ResumeSessionResponse {
    private UUID uploadSessionId;
    private UUID fileId;
    private String objectKey;
    private String uploadId;
    /** Parts that are already confirmed as UPLOADED — client can skip these. */
    private List<UploadedPartDto> uploadedParts;
    /** Fresh presigned PUT URLs for every part still PENDING — client must upload these. */
    private List<PresignPartResponse> pendingPartUrls;

    @Data
    @AllArgsConstructor
    public static class UploadedPartDto {
        private int partNumber;
        private String eTag;
        private Long sizeBytes;
    }
}
