package org.example.service;

import org.example.dto.*;
import org.example.model.FileMetadata;
import org.example.model.MultipartUploadSession;
import org.example.model.User;
import org.example.repository.FileMetadataRepository;
import org.example.repository.MultipartUploadSessionRepository;
import org.example.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.model.CompletedPart;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class MultipartUploadService {

    private final R2Service r2Service;
    private final FileMetadataRepository fileMetadataRepository;
    private final MultipartUploadSessionRepository sessionRepository;
    private final UserRepository userRepository;

    public MultipartUploadService(R2Service r2Service,
                                  FileMetadataRepository fileMetadataRepository,
                                  MultipartUploadSessionRepository sessionRepository,
                                  UserRepository userRepository) {
        this.r2Service = r2Service;
        this.fileMetadataRepository = fileMetadataRepository;
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
    }

    /**
     * Step 1 – Initiate multipart upload.
     * <p>
     * Creates a PENDING FileMetadata record, calls R2 CreateMultipartUpload, then
     * pre-generates a presigned PUT URL for <em>every</em> part so the client can
     * upload all chunks concurrently without any additional round-trips.
     * <p>
     * If {@code request.contentHash} is provided and a READY file with that hash already
     * exists, a new metadata record pointing to the same R2 object is created immediately —
     * no multipart upload is started and {@code deduplicated = true} is returned.
     */
    @Transactional
    public InitiateMultipartResponse initiate(InitiateMultipartRequest request, User caller) {
        User owner = userRepository.findById(request.getOwnerId())
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + request.getOwnerId()));

        if (!owner.getId().equals(caller.getId())) {
            throw new IllegalArgumentException("Access denied: ownerId does not match the authenticated user");
        }

        // --- Deduplication check (runs before partCount validation so clients can omit partCount) ---
        if (request.getContentHash() != null && !request.getContentHash().isBlank()) {
            var existing = fileMetadataRepository
                    .findFirstByContentHash(request.getContentHash());
            if (existing.isPresent()) {
                FileMetadata source = existing.get();
                UUID fileId = UUID.randomUUID();

                FileMetadata deduped = new FileMetadata();
                deduped.setId(fileId);
                deduped.setOwner(owner);
                deduped.setOriginalName(request.getOriginalName());
                deduped.setObjectKey(source.getObjectKey());   // reuse existing blob
                deduped.setSizeBytes(request.getSizeBytes() != null ? request.getSizeBytes() : source.getSizeBytes());
                deduped.setContentType(request.getContentType() != null ? request.getContentType() : source.getContentType());
                deduped.setContentHash(request.getContentHash());
                deduped.setStatus("READY");
                deduped.setCreatedAt(OffsetDateTime.now());
                fileMetadataRepository.save(deduped);

                // Return immediately — no R2 upload session needed
                return new InitiateMultipartResponse(null, fileId, source.getObjectKey(), null, null, true);
            }
        }

        if (request.getPartCount() < 1 || request.getPartCount() > 10_000) {
            throw new IllegalArgumentException("partCount must be between 1 and 10 000");
        }

        UUID fileId = UUID.randomUUID();
        String objectKey = owner.getId() + "/" + fileId + "/" + request.getOriginalName();

        // Pre-create FileMetadata with status PENDING
        FileMetadata file = new FileMetadata();
        file.setId(fileId);
        file.setOwner(owner);
        file.setOriginalName(request.getOriginalName());
        file.setObjectKey(objectKey);
        file.setSizeBytes(request.getSizeBytes());
        file.setContentType(request.getContentType());
        file.setContentHash(request.getContentHash());
        file.setStatus("PENDING");
        file.setCreatedAt(OffsetDateTime.now());
        fileMetadataRepository.save(file);

        // Call R2 to initiate the multipart upload
        String uploadId = r2Service.initiateMultipartUpload(objectKey, request.getContentType());

        // Pre-generate ALL presigned part URLs in one go
        List<PresignPartResponse> partUrls = r2Service
                .generateAllUploadPartUrls(objectKey, uploadId, request.getPartCount())
                .stream()
                .map(p -> new PresignPartResponse(p.partNumber(), p.presignedUrl()))
                .toList();

        // Persist the session
        UUID sessionId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        MultipartUploadSession session = new MultipartUploadSession();
        session.setId(sessionId);
        session.setFile(file);
        session.setOwner(owner);
        session.setObjectKey(objectKey);
        session.setUploadId(uploadId);
        session.setStatus("IN_PROGRESS");
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        sessionRepository.save(session);

        return new InitiateMultipartResponse(sessionId, fileId, objectKey, uploadId, partUrls, false);
    }

    /**
     * Step 2 – Get a presigned URL for a specific part.
     * <p>
     * The client calls this endpoint for each chunk it wants to upload
     * (part numbers 1..10 000, each chunk ≥ 5 MB except the last).
     */
    public PresignPartResponse presignPart(PresignPartRequest request, User caller) {
        MultipartUploadSession session = getActiveSession(request.getUploadSessionId(), caller);

        String presignedUrl = r2Service.generateUploadPartUrl(
                session.getObjectKey(),
                session.getUploadId(),
                request.getPartNumber()
        );

        return new PresignPartResponse(request.getPartNumber(), presignedUrl);
    }

    /**
     * Step 3 – Complete the multipart upload.
     * <p>
     * Calls R2 CompleteMultipartUpload, marks FileMetadata as READY,
     * and marks the session as COMPLETED.
     */
    @Transactional
    public CompleteMultipartResponse complete(CompleteMultipartRequest request, User caller) {
        MultipartUploadSession session = getActiveSession(request.getUploadSessionId(), caller);

        // Build the list of CompletedPart objects from the client-supplied ETags.
        // R2/S3 requires ETags to be surrounded by double-quotes (e.g. "abc123").
        // Some HTTP clients strip the quotes, so we normalise them here.
        List<CompletedPart> completedParts = request.getParts().stream()
                .map(p -> CompletedPart.builder()
                        .partNumber(p.getPartNumber())
                        .eTag(normalizeETag(p.getETag()))
                        .build())
                .toList();

        // Tell R2 to assemble the object
        r2Service.completeMultipartUpload(session.getObjectKey(), session.getUploadId(), completedParts);

        // Mark file as READY
        FileMetadata file = session.getFile();
        file.setStatus("READY");
        fileMetadataRepository.save(file);

        // Mark session as COMPLETED
        session.setStatus("COMPLETED");
        session.setUpdatedAt(OffsetDateTime.now());
        sessionRepository.save(session);

        return new CompleteMultipartResponse(file.getId(), file.getObjectKey(), "READY");
    }

    /**
     * Abort – cancel the multipart upload and clean up.
     * <p>
     * Calls R2 AbortMultipartUpload, soft-deletes the FileMetadata record,
     * and marks the session as ABORTED.
     */
    @Transactional
    public void abort(AbortMultipartRequest request, User caller) {
        MultipartUploadSession session = getActiveSession(request.getUploadSessionId(), caller);

        // Tell R2 to discard all uploaded parts
        r2Service.abortMultipartUpload(session.getObjectKey(), session.getUploadId());

        // Soft-delete the pre-created file record
        FileMetadata file = session.getFile();
        file.setIsDeleted("Y");
        file.setStatus("ABORTED");
        fileMetadataRepository.save(file);

        // Mark session as ABORTED
        session.setStatus("ABORTED");
        session.setUpdatedAt(OffsetDateTime.now());
        sessionRepository.save(session);
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    private MultipartUploadSession getActiveSession(UUID sessionId, User caller) {
        MultipartUploadSession session = sessionRepository
                .findByIdAndStatus(sessionId, "IN_PROGRESS")
                .orElseThrow(() -> new IllegalArgumentException(
                        "No active upload session found: " + sessionId));

        if (!session.getOwner().getId().equals(caller.getId())) {
            throw new IllegalArgumentException("Access denied: session does not belong to the authenticated user");
        }

        return session;
    }

    /**
     * S3/R2 requires ETag values inside the CompleteMultipartUpload XML body to be
     * wrapped in exactly one pair of double-quotes, e.g. {@code "abc123"}.
     * The AWS SDK v2 serialises {@link CompletedPart#eTag()} as-is into the XML,
     * so we must normalise the value ourselves.
     * <p>
     * The UI already sends pre-quoted ETags (e.g. {@code "\"abc123\""}), so naively
     * passing them through results in double-wrapped XML ({@code ""abc123""}),
     * which causes the 400 "not well-formed XML" error from R2/S3.
     * <p>
     * Fix: strip every layer of surrounding double-quotes first to obtain the raw
     * hex digest, then re-wrap with exactly one pair of quotes.
     */
    private String normalizeETag(String eTag) {
        if (eTag == null) return null;
        String trimmed = eTag.trim();
        // Strip every layer of surrounding double-quotes to get the raw hex value
        while (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        // Re-wrap with exactly one pair — required by the S3/R2 XML schema
        return "\"" + trimmed + "\"";
    }
}
