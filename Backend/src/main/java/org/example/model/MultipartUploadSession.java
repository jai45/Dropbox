package org.example.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "multipart_upload_sessions")
public class MultipartUploadSession {

    @Id
    private UUID id;

    /** Pre-created FileMetadata record (status = PENDING until the upload is completed). */
    @ManyToOne
    @JoinColumn(name = "file_id", nullable = false)
    private FileMetadata file;

    @ManyToOne
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(name = "object_key", nullable = false)
    private String objectKey;

    /** The R2 multipart upload ID returned by CreateMultipartUpload. */
    @Column(name = "upload_id", nullable = false)
    private String uploadId;

    /** IN_PROGRESS | COMPLETED | ABORTED */
    @Column(nullable = false)
    private String status = "IN_PROGRESS";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
