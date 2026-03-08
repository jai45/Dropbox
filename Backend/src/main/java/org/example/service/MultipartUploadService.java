package org.example.service;

import org.example.dto.*;
import org.example.model.FileMetadata;
import org.example.model.MultipartUploadPart;
import org.example.model.MultipartUploadSession;
import org.example.model.User;
import org.example.repository.FileMetadataRepository;
import org.example.repository.MultipartUploadPartRepository;
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
    private final MultipartUploadPartRepository partRepository;
    private final UserRepository userRepository;

    public MultipartUploadService(R2Service r2Service,
                                  FileMetadataRepository fileMetadataRepository,
                                  MultipartUploadSessionRepository sessionRepository,
                                  MultipartUploadPartRepository partRepository,
                                  UserRepository userRepository) {
        this.r2Service = r2Service;
        this.fileMetadataRepository = fileMetadataRepository;
        this.sessionRepository = sessionRepository;
        this.partRepository = partRepository;
        this.userRepository = userRepository;
    }

    /**
     * Step 1 – Initiate (or transparently resume) a multipart upload.
     *
     * <p>Three outcomes, decided in order:
     * <ol>
     *   <li><b>Deduplication</b> — a COMPLETED file with the same {@code contentHash} already exists.
     *       A new FileMetadata record pointing to the same R2 object is created instantly.
     *       {@code deduplicated=true}, no upload needed.</li>
     *   <li><b>Resume</b> — an IN_PROGRESS session for the same owner + {@code contentHash} exists.
     *       Fresh presigned URLs are generated <em>only</em> for still-PENDING parts.
     *       {@code resumed=true}; {@code uploadedPartNumbers} lists the parts the client can skip.</li>
     *   <li><b>Fresh upload</b> — no matching session or file found.
     *       A new FileMetadata + session are created and presigned URLs for all parts are returned.</li>
     * </ol>
     */
    @Transactional
    public InitiateMultipartResponse initiate(InitiateMultipartRequest request, User caller) {
        User owner = userRepository.findById(request.getOwnerId())
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + request.getOwnerId()));

        if (!owner.getId().equals(caller.getId())) {
            throw new IllegalArgumentException("Access denied: ownerId does not match the authenticated user");
        }

        // ── 1. Deduplication ──────────────────────────────────────────────────────
        if (request.getContentHash() != null && !request.getContentHash().isBlank()) {
            var existing = fileMetadataRepository
                    .findFirstByContentHashAndStatus(request.getContentHash(), "COMPLETED");
            if (existing.isPresent()) {
                FileMetadata source = existing.get();
                UUID fileId = UUID.randomUUID();

                FileMetadata deduped = new FileMetadata();
                deduped.setId(fileId);
                deduped.setOwner(owner);
                deduped.setOriginalName(request.getOriginalName());
                deduped.setObjectKey(source.getObjectKey());
                deduped.setSizeBytes(request.getSizeBytes() != null ? request.getSizeBytes() : source.getSizeBytes());
                deduped.setContentType(request.getContentType() != null ? request.getContentType() : source.getContentType());
                deduped.setContentHash(request.getContentHash());
                deduped.setStatus("READY");
                deduped.setCreatedAt(OffsetDateTime.now());
                fileMetadataRepository.save(deduped);

                return new InitiateMultipartResponse(
                        null, fileId, source.getObjectKey(), null, null, true, false, null);
            }

            // ── 2. Resume ─────────────────────────────────────────────────────────
            var activeSession = sessionRepository
                    .findInProgressByOwnerAndContentHash(owner.getId(), request.getContentHash());
            if (activeSession.isPresent()) {
                MultipartUploadSession session = activeSession.get();

                List<MultipartUploadPart> allParts =
                        partRepository.findBySessionIdOrderByPartNumber(session.getId());

                // Already-done parts — tell the client to skip these
                List<Integer> uploadedPartNumbers = allParts.stream()
                        .filter(p -> "UPLOADED".equals(p.getStatus()))
                        .map(MultipartUploadPart::getPartNumber)
                        .toList();

                // Still-pending parts — generate fresh presigned URLs
                List<PresignPartResponse> pendingUrls = allParts.stream()
                        .filter(p -> "PENDING".equals(p.getStatus()))
                        .map(p -> new PresignPartResponse(
                                p.getPartNumber(),
                                r2Service.generateUploadPartUrl(
                                        session.getObjectKey(),
                                        session.getUploadId(),
                                        p.getPartNumber())))
                        .toList();

                // Touch updatedAt so the session looks active
                session.setUpdatedAt(OffsetDateTime.now());
                sessionRepository.save(session);

                return new InitiateMultipartResponse(
                        session.getId(),
                        session.getFile().getId(),
                        session.getObjectKey(),
                        session.getUploadId(),
                        pendingUrls,
                        false,
                        true,
                        uploadedPartNumbers);
            }
        }

        // ── 3. Fresh upload ───────────────────────────────────────────────────────
        if (request.getPartCount() < 1 || request.getPartCount() > 10_000) {
            throw new IllegalArgumentException("partCount must be between 1 and 10 000");
        }

        UUID fileId = UUID.randomUUID();
        String objectKey = owner.getId() + "/" + fileId + "/" + request.getOriginalName();

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

        String uploadId = r2Service.initiateMultipartUpload(objectKey, request.getContentType());

        List<PresignPartResponse> partUrls = r2Service
                .generateAllUploadPartUrls(objectKey, uploadId, request.getPartCount())
                .stream()
                .map(p -> new PresignPartResponse(p.partNumber(), p.presignedUrl()))
                .toList();

        UUID sessionId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        MultipartUploadSession session = new MultipartUploadSession();
        session.setId(sessionId);
        session.setFile(file);
        session.setOwner(owner);
        session.setObjectKey(objectKey);
        session.setUploadId(uploadId);
        session.setPartCount(request.getPartCount());
        session.setStatus("IN_PROGRESS");
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        sessionRepository.saveAndFlush(session);  // flush so the session row exists before parts reference it

        // Pre-create a PENDING row for every part so we can track resume progress
        List<MultipartUploadPart> pendingParts = new java.util.ArrayList<>();
        for (int i = 1; i <= request.getPartCount(); i++) {
            MultipartUploadPart part = new MultipartUploadPart();
            part.setId(UUID.randomUUID());         // assign ID explicitly — no @GeneratedValue
            part.setSession(session);
            part.setPartNumber(i);
            part.setStatus("PENDING");
            part.setCreatedAt(now);
            pendingParts.add(part);
        }
        partRepository.saveAll(pendingParts);

        return new InitiateMultipartResponse(
                sessionId, fileId, objectKey, uploadId, partUrls, false, false, null);
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

        // Always source the ETags from the DB — this is the only safe approach when
        // an upload may have been resumed across multiple sessions.
        // If the client sends parts[] we ignore them; every part must have been
        // confirmed via /confirm-part so the DB is the single source of truth.
        List<MultipartUploadPart> confirmedParts =
                partRepository.findBySessionIdAndStatusOrderByPartNumber(session.getId(), "UPLOADED");

        if (confirmedParts.isEmpty()) {
            throw new IllegalStateException(
                    "No confirmed parts found for session " + session.getId() +
                    ". Upload all parts and confirm each one via /confirm-part before completing.");
        }

        // Verify all expected parts are accounted for
        if (confirmedParts.size() != session.getPartCount()) {
            throw new IllegalStateException(
                    "Incomplete upload: " + confirmedParts.size() + " of " +
                    session.getPartCount() + " parts confirmed. " +
                    "Upload and confirm all remaining parts before completing.");
        }

        List<CompletedPart> completedParts = confirmedParts.stream()
                .map(p -> CompletedPart.builder()
                        .partNumber(p.getPartNumber())
                        .eTag(normalizeETag(p.getETag()))
                        .build())
                .toList();

        // Tell R2 to assemble the object from ALL confirmed parts
        r2Service.completeMultipartUpload(session.getObjectKey(), session.getUploadId(), completedParts);

        // Mark file as COMPLETED
        FileMetadata file = session.getFile();
        file.setStatus("COMPLETED");
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

    /**
     * Step 2b – Confirm that a single part has been successfully uploaded to R2.
     * <p>
     * The client calls this after every successful PUT so the backend can track
     * progress and enable resumable uploads.
     */
    @Transactional
    public ConfirmPartResponse confirmPart(ConfirmPartRequest request, User caller) {
        MultipartUploadSession session = getActiveSession(request.getUploadSessionId(), caller);

        MultipartUploadPart part = partRepository
                .findBySessionIdAndPartNumber(session.getId(), request.getPartNumber())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Part " + request.getPartNumber() + " not found for session " + session.getId()));

        part.setETag(request.getETag());
        part.setSizeBytes(request.getSizeBytes());
        part.setStatus("UPLOADED");
        part.setUploadedAt(OffsetDateTime.now());
        partRepository.save(part);

        return new ConfirmPartResponse(
                session.getId(),
                request.getPartNumber(),
                "UPLOADED",
                0, // the client already knows the ETag and size, so no need to return them here
                session.getPartCount()
        );
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
