package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
public class PresignResponse {
    private UUID fileId;
    private String objectKey;
    private String uploadUrl;   // presigned PUT URL for client to upload directly to R2
}
