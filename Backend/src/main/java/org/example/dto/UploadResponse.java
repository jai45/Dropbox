package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
public class UploadResponse {

    private UUID userId;
    private UUID fileId;
    private String message;
}
