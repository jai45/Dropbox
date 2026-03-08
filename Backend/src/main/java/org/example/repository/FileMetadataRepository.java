package org.example.repository;

import org.example.model.FileMetadata;
import org.example.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FileMetadataRepository extends JpaRepository<FileMetadata, UUID> {

    // Only return non-deleted files for the user
    List<FileMetadata> findByOwnerAndStatusAndIsDeletedOrderByCreatedAtDesc(User owner, String status, String isDeleted);

    // Find a single file only if not deleted
    Optional<FileMetadata> findByIdAndIsDeleted(UUID id, String isDeleted);

    // Find any READY, non-deleted file with the given content hash for deduplication
    Optional<FileMetadata> findFirstByContentHashAndStatus(
            String contentHash, String status);
}
