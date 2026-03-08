package org.example.service;

import org.example.dto.ConfirmUploadRequest;
import org.example.dto.DownloadUrlResponse;
import org.example.dto.FileDto;
import org.example.dto.PresignRequest;
import org.example.dto.PresignResponse;
import org.example.model.FileMetadata;
import org.example.model.User;
import org.example.repository.FileMetadataRepository;
import org.example.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class FileService {

    private final FileMetadataRepository fileMetadataRepository;
    private final UserRepository userRepository;
    private final R2Service r2Service;

    public FileService(FileMetadataRepository fileMetadataRepository,
                       UserRepository userRepository,
                       R2Service r2Service) {
        this.fileMetadataRepository = fileMetadataRepository;
        this.userRepository = userRepository;
        this.r2Service = r2Service;
    }

    /**
     * Saves file metadata to DB and returns a presigned PUT URL for direct R2 upload.
     * <p>
     * If {@code request.contentHash} is provided and a READY file with that hash already
     * exists, a new metadata record is created pointing to the <em>same</em> R2 object —
     * no presigned URL is generated and {@code deduplicated = true} is returned.
     */
    @Transactional
    public PresignResponse createPresignedUpload(PresignRequest request) {
        User owner = userRepository.findById(request.getOwnerId())
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + request.getOwnerId()));

        // --- Deduplication check ---
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
                deduped.setObjectKey(source.getObjectKey());   // reuse existing blob
                deduped.setSizeBytes(request.getSizeBytes() != null ? request.getSizeBytes() : source.getSizeBytes());
                deduped.setContentType(request.getContentType() != null ? request.getContentType() : source.getContentType());
                deduped.setContentHash(request.getContentHash());
                deduped.setStatus("READY");
                deduped.setCreatedAt(OffsetDateTime.now());
                fileMetadataRepository.save(deduped);

                // Return immediately — no upload needed
                return new PresignResponse(fileId, source.getObjectKey(), null, true);
            }
        }

        UUID fileId = UUID.randomUUID();
        // object key pattern: <ownerId>/<fileId>/<originalName>
        String objectKey = request.getOwnerId() + "/" + fileId + "/" + request.getOriginalName();

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

        String uploadUrl = r2Service.generateUploadUrl(objectKey, request.getContentType());
        return new PresignResponse(fileId, objectKey, uploadUrl, false);
    }

    /**
     * Called by the client after it has finished uploading directly to R2.
     * Transitions the file status PENDING → READY and stamps content_hash
     * (if not already set) so future uploads of the same content are deduplicated.
     */
    @Transactional
    public void confirmUpload(UUID fileId, User owner, ConfirmUploadRequest request) {
        FileMetadata file = fileMetadataRepository.findByIdAndIsDeleted(fileId, "N")
                .orElseThrow(() -> new IllegalArgumentException("File not found: " + fileId));

        if (!file.getOwner().getId().equals(owner.getId())) {
            throw new IllegalArgumentException("Access denied");
        }

        file.setStatus("COMPLETED");

        // Stamp the hash if it wasn't already provided during presign
        if (file.getContentHash() == null
                && request != null
                && request.getContentHash() != null
                && !request.getContentHash().isBlank()) {
            file.setContentHash(request.getContentHash());
        }

        fileMetadataRepository.save(file);
    }

    /**
     * Generates a presigned GET URL for an existing file and returns it as a JSON response.
     */
    public DownloadUrlResponse getDownloadUrl(UUID fileId) {
        FileMetadata file = fileMetadataRepository.findByIdAndIsDeleted(fileId, "N")
                .orElseThrow(() -> new IllegalArgumentException("File not found: " + fileId));
        String downloadUrl = r2Service.generateDownloadUrl(file.getObjectKey(), file.getOriginalName());
        return new DownloadUrlResponse(file.getId(), file.getOriginalName(), downloadUrl);
    }

    /**
     * Returns all non-deleted files belonging to the given user mapped to FileDto.
     */
    public List<FileDto> listFiles(User owner) {
        return fileMetadataRepository.findByOwnerAndIsDeletedOrderByCreatedAtDesc(owner, "N")
                .stream()
                .map(f -> new FileDto(
                        f.getId(),
                        f.getOriginalName(),
                        f.getSizeBytes(),
                        f.getContentType(),
                        f.getCreatedAt(),
                        f.getFolder() != null ? f.getFolder().getId() : null,
                        f.getObjectKey(),
                        f.getStatus()
                ))
                .toList();
    }

    /**
     * Soft-deletes a file by setting isDeleted = 'Y'. File remains in R2.
     * Throws if the file doesn't exist, is already deleted, or doesn't belong to the owner.
     */
    @Transactional
    public void deleteFile(UUID fileId, User owner) {
        FileMetadata file = fileMetadataRepository.findByIdAndIsDeleted(fileId, "N")
                .orElseThrow(() -> new IllegalArgumentException("File not found: " + fileId));

        if (!file.getOwner().getId().equals(owner.getId())) {
            throw new IllegalArgumentException("Access denied");
        }

        file.setIsDeleted("Y");
        fileMetadataRepository.save(file);
    }
}
