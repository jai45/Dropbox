package org.example.service;

import org.example.dto.DownloadUrlResponse;
import org.example.dto.PresignRequest;
import org.example.dto.PresignResponse;
import org.example.model.FileMetadata;
import org.example.model.User;
import org.example.repository.FileMetadataRepository;
import org.example.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
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
     */
    @Transactional
    public PresignResponse createPresignedUpload(PresignRequest request) {
        User owner = userRepository.findById(request.getOwnerId())
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + request.getOwnerId()));

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
        file.setStatus("PENDING");
        file.setCreatedAt(OffsetDateTime.now());
        fileMetadataRepository.save(file);

        String uploadUrl = r2Service.generateUploadUrl(objectKey, request.getContentType());
        return new PresignResponse(fileId, objectKey, uploadUrl);
    }

    /**
     * Generates a presigned GET URL for an existing file and returns it as a JSON response.
     */
    public DownloadUrlResponse getDownloadUrl(UUID fileId) {
        FileMetadata file = fileMetadataRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("File not found: " + fileId));
        String downloadUrl = r2Service.generateDownloadUrl(file.getObjectKey(), file.getOriginalName());
        return new DownloadUrlResponse(file.getId(), file.getOriginalName(), downloadUrl);
    }
}
