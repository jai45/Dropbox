package org.example.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class CompleteMultipartRequest {
    private UUID uploadSessionId;
    // ETags are no longer accepted from the client — they are sourced exclusively
    // from the DB (confirmed via /confirm-part) to guarantee correctness across resumes.
}
