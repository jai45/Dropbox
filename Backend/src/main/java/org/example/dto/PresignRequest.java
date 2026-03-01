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
}
