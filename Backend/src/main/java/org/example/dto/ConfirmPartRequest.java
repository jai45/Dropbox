package org.example.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.UUID;

/**
 * Request body for POST /api/v1/multipart/confirm-part.
 * The client sends this after a successful part PUT to R2.
 */
@Data
public class ConfirmPartRequest {
    private UUID uploadSessionId;
    /** 1-based part number that was just uploaded. */
    private int partNumber;
    /** ETag header value returned by R2 in the PUT response. */
    @JsonProperty("eTag")
    private String eTag;
    /** Size of this part in bytes (optional but recommended for tracking). */
    private Long sizeBytes;
}
