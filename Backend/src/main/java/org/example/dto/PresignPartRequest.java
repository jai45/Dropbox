package org.example.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class PresignPartRequest {
    private UUID uploadSessionId;
    private int partNumber;   // 1-based, max 10 000
    private Long partSize;    // size of this part in bytes (informational)
}
