package org.example.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class CompleteMultipartRequest {
    private UUID uploadSessionId;
    /** Ordered list of completed parts. Each entry carries the 1-based part number and the ETag returned by R2. */
    private List<CompletedPartDto> parts;

    @Data
    public static class CompletedPartDto {
        private int partNumber;
        @JsonProperty("eTag")
        private String eTag;
    }
}
