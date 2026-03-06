package org.example.repository;

import org.example.model.MultipartUploadSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MultipartUploadSessionRepository extends JpaRepository<MultipartUploadSession, UUID> {

    Optional<MultipartUploadSession> findByIdAndStatus(UUID id, String status);
}
