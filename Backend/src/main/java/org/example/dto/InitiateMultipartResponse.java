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
    /**
     * Presigned PUT URLs for parts that still need to be uploaded, ordered by partNumber (1-based).
     * On a fresh upload this covers all parts (1..partCount).
     * On a resumed upload this covers only the PENDING parts — already-UPLOADED parts are omitted.
     * Null when deduplicated.
     */
    private List<PresignPartResponse> parts;
    /** True when the file already existed in storage — no upload is needed. */
    private boolean deduplicated;
    /**
     * True when an interrupted upload for the same content hash was found and resumed.
     * The {@code parts} list contains only the parts that still need to be uploaded.
     * Already-uploaded part numbers are listed in {@code uploadedPartNumbers}.
     */
    private boolean resumed;
    /**
     * Part numbers that were already successfully uploaded in the previous attempt.
     * The client should skip these parts entirely.
     * Null on a fresh upload or deduplication.
     */
    private List<Integer> uploadedPartNumbers;
}
