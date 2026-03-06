package org.example.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class AbortMultipartRequest {
    private UUID uploadSessionId;
}
