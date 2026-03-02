package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
public class DownloadUrlResponse {
    private UUID fileId;
    private String originalName;
    private String downloadUrl;
}
