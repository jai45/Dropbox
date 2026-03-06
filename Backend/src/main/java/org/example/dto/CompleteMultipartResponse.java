package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
public class CompleteMultipartResponse {
    private UUID fileId;
    private String objectKey;
    private String status; // "READY"
}
