package org.example.dto;

import lombok.Data;

@Data
public class UploadRequest {

    // User fields
    private String email;
    private String passwordHash;

    // File metadata fields
    private String originalName;
    private String objectKey;
    private Long sizeBytes;
    private String contentType;
    private String status;
}
