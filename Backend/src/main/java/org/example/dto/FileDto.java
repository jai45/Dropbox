package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
public class FileDto {
    private UUID id;
    private String name;
    private Long size;
    private String type;
    private OffsetDateTime uploadedAt;
    private UUID folderId;
    private String objectKey;
    private String status;
}
