package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PresignPartResponse {
    private int partNumber;
    private String presignedUrl; // presigned PUT URL for this part
}
