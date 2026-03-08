package org.example.repository;

import org.example.model.MultipartUploadPart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MultipartUploadPartRepository extends JpaRepository<MultipartUploadPart, UUID> {

    /** All parts for a session, ordered by part number. */
    List<MultipartUploadPart> findBySessionIdOrderByPartNumber(UUID sessionId);

    /** All UPLOADED parts for a session — used when assembling the final CompleteMultipartUpload call. */
    List<MultipartUploadPart> findBySessionIdAndStatusOrderByPartNumber(UUID sessionId, String status);

    /** Lookup a specific part inside a session. */
    Optional<MultipartUploadPart> findBySessionIdAndPartNumber(UUID sessionId, int partNumber);
}
