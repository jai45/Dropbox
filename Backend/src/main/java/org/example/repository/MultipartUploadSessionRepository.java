package org.example.repository;

import org.example.model.MultipartUploadSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MultipartUploadSessionRepository extends JpaRepository<MultipartUploadSession, UUID> {

    Optional<MultipartUploadSession> findByIdAndStatus(UUID id, String status);

    /**
     * Find an active (IN_PROGRESS) session that belongs to the given owner and
     * whose associated file has the given content hash.
     * Used by the initiate flow to transparently resume an interrupted upload.
     */
    @Query("SELECT s FROM MultipartUploadSession s " +
           "WHERE s.owner.id = :ownerId " +
           "AND s.file.contentHash = :contentHash " +
           "AND s.status = 'IN_PROGRESS' " +
           "ORDER BY s.createdAt DESC")
    Optional<MultipartUploadSession> findInProgressByOwnerAndContentHash(
            @Param("ownerId") UUID ownerId,
            @Param("contentHash") String contentHash);
}
