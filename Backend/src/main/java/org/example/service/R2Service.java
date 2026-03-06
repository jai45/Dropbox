package org.example.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedUploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

import java.time.Duration;
import java.util.List;

@Service
public class R2Service {

    private final S3Presigner s3Presigner;
    private final S3Client s3Client;

    @Value("${r2.bucket}")
    private String bucket;

    @Value("${r2.presign-duration-minutes}")
    private long presignDurationMinutes;

    public R2Service(S3Presigner s3Presigner, S3Client s3Client) {
        this.s3Presigner = s3Presigner;
        this.s3Client = s3Client;
    }

    /**
     * Generates a presigned PUT URL so the client can upload directly to R2.
     */
    public String generateUploadUrl(String objectKey, String contentType) {
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(contentType)
                .build();

        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(
                PutObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(presignDurationMinutes))
                        .putObjectRequest(putRequest)
                        .build()
        );

        return presigned.url().toString();
    }

    /**
     * Generates a presigned GET URL so the client can download directly from R2.
     */
    public String generateDownloadUrl(String objectKey, String originalName) {
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .responseContentDisposition("attachment; filename=\"" + originalName + "\"")
                .build();

        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(
                GetObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(presignDurationMinutes))
                        .getObjectRequest(getRequest)
                        .build()
        );

        return presigned.url().toString();
    }

    // -----------------------------------------------------------------------
    // Multipart Upload helpers
    // -----------------------------------------------------------------------

    /**
     * Calls CreateMultipartUpload and returns the R2-assigned upload ID.
     */
    public String initiateMultipartUpload(String objectKey, String contentType) {
        CreateMultipartUploadRequest request = CreateMultipartUploadRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(contentType)
                .build();

        CreateMultipartUploadResponse response = s3Client.createMultipartUpload(request);
        return response.uploadId();
    }

    /**
     * Returns a presigned PUT URL for a single part.
     *
     * @param objectKey  the R2 object key
     * @param uploadId   the multipart upload ID from {@link #initiateMultipartUpload}
     * @param partNumber 1-based part number (max 10 000)
     */
    public String generateUploadPartUrl(String objectKey, String uploadId, int partNumber) {
        UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .uploadId(uploadId)
                .partNumber(partNumber)
                .build();

        PresignedUploadPartRequest presigned = s3Presigner.presignUploadPart(
                UploadPartPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(presignDurationMinutes))
                        .uploadPartRequest(uploadPartRequest)
                        .build()
        );

        return presigned.url().toString();
    }

    /**
     * Pre-generates presigned PUT URLs for ALL parts in one shot so the client
     * can upload every chunk concurrently without making additional round-trips.
     *
     * @param objectKey  the R2 object key
     * @param uploadId   the multipart upload ID
     * @param partCount  total number of parts (1-based, max 10 000)
     * @return ordered list of {@code (partNumber, presignedUrl)} — index 0 → part 1
     */
    public List<PartPresignResult> generateAllUploadPartUrls(String objectKey, String uploadId, int partCount) {
        List<PartPresignResult> results = new java.util.ArrayList<>(partCount);
        for (int partNumber = 1; partNumber <= partCount; partNumber++) {
            String url = generateUploadPartUrl(objectKey, uploadId, partNumber);
            results.add(new PartPresignResult(partNumber, url));
        }
        return results;
    }

    /** Simple value holder returned by {@link #generateAllUploadPartUrls}. */
    public record PartPresignResult(int partNumber, String presignedUrl) {}

    /**
     * Calls CompleteMultipartUpload to assemble the final object in R2.
     *
     * @param objectKey      the R2 object key
     * @param uploadId       the multipart upload ID
     * @param completedParts ordered list of {@link CompletedPart} (partNumber + eTag)
     */
    public void completeMultipartUpload(String objectKey, String uploadId,
                                        List<CompletedPart> completedParts) {
        CompletedMultipartUpload completedUpload = CompletedMultipartUpload.builder()
                .parts(completedParts)
                .build();
        
        CompleteMultipartUploadRequest request = CompleteMultipartUploadRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .uploadId(uploadId)
                .multipartUpload(completedUpload)
                .build();

        s3Client.completeMultipartUpload(request);
    }

    /**
     * Aborts an in-progress multipart upload, freeing stored parts in R2.
     */
    public void abortMultipartUpload(String objectKey, String uploadId) {
        AbortMultipartUploadRequest request = AbortMultipartUploadRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .uploadId(uploadId)
                .build();

        s3Client.abortMultipartUpload(request);
    }
}
