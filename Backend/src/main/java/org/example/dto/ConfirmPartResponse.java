package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

/** Response for POST /api/v1/multipart/confirm-part. */
@Data
@AllArgsConstructor
public class ConfirmPartResponse {
    private UUID uploadSessionId;
    private int partNumber;
    /** Confirmed status — always "UPLOADED" on success. */
    private String status;
    /** How many parts have been uploaded so far. */
    private int uploadedCount;
    /** Total parts expected (== the partCount supplied at initiation). */
    private int totalParts;
}
