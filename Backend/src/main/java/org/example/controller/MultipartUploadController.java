package org.example.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.dto.*;
import org.example.model.User;
import org.example.service.MultipartUploadService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Multipart Upload", description = "S3-compatible multipart upload to Cloudflare R2")
@RestController
@RequestMapping("/api/v1/multipart")
public class MultipartUploadController {

    private final MultipartUploadService multipartUploadService;

    public MultipartUploadController(MultipartUploadService multipartUploadService) {
        this.multipartUploadService = multipartUploadService;
    }

    @Operation(
            summary = "Initiate a multipart upload",
            description = "Creates a pending FileMetadata record, calls R2 CreateMultipartUpload, " +
                          "and returns a presigned PUT URL for **every** part in one response. " +
                          "The client can then upload all chunks to R2 concurrently without any " +
                          "additional round-trips. Supply `partCount` = ceil(fileSize / chunkSize); " +
                          "each part must be ≥ 5 MB except the last.",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "201", description = "Multipart upload initiated — all part URLs included",
                            content = @Content(schema = @Schema(implementation = InitiateMultipartResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid request or access denied")
            }
    )
    @PostMapping("/initiate")
    public ResponseEntity<InitiateMultipartResponse> initiate(
            @RequestBody InitiateMultipartRequest request,
            @AuthenticationPrincipal User caller) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(multipartUploadService.initiate(request, caller));
    }

    @Operation(
            summary = "Get a presigned URL for a single part",
            description = "Returns a presigned PUT URL the client uses to upload one chunk directly to R2. " +
                          "Parts must be ≥ 5 MB except for the last part.",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Presigned URL returned",
                            content = @Content(schema = @Schema(implementation = PresignPartResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Session not found or access denied")
            }
    )
    @PostMapping("/presign-part")
    public ResponseEntity<PresignPartResponse> presignPart(
            @RequestBody PresignPartRequest request,
            @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(multipartUploadService.presignPart(request, caller));
    }

    @Operation(
            summary = "Complete the multipart upload",
            description = "Passes all part ETags to R2 to assemble the final object. " +
                          "FileMetadata status is updated to READY.",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Upload completed successfully",
                            content = @Content(schema = @Schema(implementation = CompleteMultipartResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Session not found, access denied, or R2 error")
            }
    )
    @PostMapping("/complete")
    public ResponseEntity<CompleteMultipartResponse> complete(
            @RequestBody CompleteMultipartRequest request,
            @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(multipartUploadService.complete(request, caller));
    }

    @Operation(
            summary = "Confirm a part upload",
            description = "Call this after every successful PUT to R2. " +
                          "The backend marks the part as UPLOADED and stores the ETag so the upload " +
                          "can be resumed if it is interrupted before completion.",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Part confirmed",
                            content = @Content(schema = @Schema(implementation = ConfirmPartResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Session or part not found, or access denied")
            }
    )
    @PostMapping("/confirm-part")
    public ResponseEntity<ConfirmPartResponse> confirmPart(
            @RequestBody ConfirmPartRequest request,
            @AuthenticationPrincipal User caller) {
        return ResponseEntity.ok(multipartUploadService.confirmPart(request, caller));
    }

    @Operation(
            summary = "Abort a multipart upload",
            description = "Calls R2 AbortMultipartUpload to discard all uploaded parts and cleans up the DB session.",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "204", description = "Upload aborted"),
                    @ApiResponse(responseCode = "400", description = "Session not found or access denied")
            }
    )
    @PostMapping("/abort")
    public ResponseEntity<Void> abort(
            @RequestBody AbortMultipartRequest request,
            @AuthenticationPrincipal User caller) {
        multipartUploadService.abort(request, caller);
        return ResponseEntity.noContent().build();
    }
}
