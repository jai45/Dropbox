package org.example.service;

import org.example.dto.UploadRequest;
import org.example.dto.UploadResponse;
import org.example.model.FileMetadata;
import org.example.model.User;
import org.example.repository.FileMetadataRepository;
import org.example.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class UploadService {

    private final UserRepository userRepository;
    private final FileMetadataRepository fileMetadataRepository;

    public UploadService(UserRepository userRepository, FileMetadataRepository fileMetadataRepository) {
        this.userRepository = userRepository;
        this.fileMetadataRepository = fileMetadataRepository;
    }

    @Transactional
    public UploadResponse processUpload(UploadRequest request) {

        // Map request to User entity
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(request.getEmail());
        user.setPasswordHash(request.getPasswordHash());
        user.setCreatedAt(OffsetDateTime.now());
        user = userRepository.save(user);

        // Map request to FileMetadata entity
        FileMetadata fileMetadata = new FileMetadata();
        fileMetadata.setId(UUID.randomUUID());
        fileMetadata.setOwner(user);
        fileMetadata.setOriginalName(request.getOriginalName());
        fileMetadata.setObjectKey(request.getObjectKey());
        fileMetadata.setSizeBytes(request.getSizeBytes());
        fileMetadata.setContentType(request.getContentType());
        fileMetadata.setStatus(request.getStatus());
        fileMetadata.setCreatedAt(OffsetDateTime.now());
        fileMetadata = fileMetadataRepository.save(fileMetadata);

        return new UploadResponse(user.getId(), fileMetadata.getId(), "Upload successful");
    }
}
