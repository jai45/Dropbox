package org.example.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Tracks the upload status of every individual part inside a multipart upload session.
 * A row is created (status=PENDING) when the session is initiated for each pre-allocated
 * part, and updated to UPLOADED once the client confirms the part via the /confirm-part API.
 */
@Data
@Entity
@Table(name = "multipart_upload_parts",
       uniqueConstraints = @UniqueConstraint(columnNames = {"session_id", "part_number"}))
public class MultipartUploadPart {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private MultipartUploadSession session;

    /** 1-based S3/R2 part number. */
    @Column(name = "part_number", nullable = false)
    private int partNumber;

    /** ETag returned by R2 after the client's PUT succeeds. Null until the part is confirmed. */
    @Column(name = "e_tag")
    private String eTag;

    /** Size of this part in bytes — provided by the client at confirmation time. */
    @Column(name = "size_bytes")
    private Long sizeBytes;

    /** PENDING | UPLOADED */
    @Column(nullable = false)
    private String status = "PENDING";

    /** Timestamp when the client confirmed the part upload. */
    @Column(name = "uploaded_at")
    private OffsetDateTime uploadedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
