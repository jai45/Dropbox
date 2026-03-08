package org.example.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.dto.ConfirmUploadRequest;
import org.example.dto.DownloadUrlResponse;
import org.example.dto.FileDto;
import org.example.dto.PresignRequest;
import org.example.dto.PresignResponse;
import org.example.model.User;
import org.example.service.FileService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Files", description = "File presign and download operations")
@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @Operation(
            summary = "Get presigned upload URL",
            description = "Saves file metadata to DB and returns a presigned PUT URL for direct upload to Cloudflare R2",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "201", description = "Presigned URL generated",
                            content = @Content(schema = @Schema(implementation = PresignResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Owner user not found")
            }
    )
    @PostMapping("/presign")
    public ResponseEntity<PresignResponse> presign(@RequestBody PresignRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(fileService.createPresignedUpload(request));
    }

    @Operation(
            summary = "Confirm single-part upload complete",
            description = "Call this after the file has been uploaded directly to R2 via the presigned PUT URL. " +
                          "Marks the file status as READY and stamps the content hash for future deduplication. " +
                          "Do NOT call this if deduplicated=true was returned from /presign — the file is already READY.",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "File marked as READY"),
                    @ApiResponse(responseCode = "400", description = "File not in PENDING state or access denied"),
                    @ApiResponse(responseCode = "404", description = "File not found")
            }
    )
    @PostMapping("/{fileId}/confirm")
    public ResponseEntity<Void> confirmUpload(@PathVariable UUID fileId,
                                              @RequestBody(required = false) ConfirmUploadRequest request,
                                              @AuthenticationPrincipal User user) {
        fileService.confirmUpload(fileId, user, request);
        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "List all files for the logged-in user",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Files returned")
            }
    )
    @GetMapping
    public ResponseEntity<java.util.List<FileDto>> listFiles(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(fileService.listFiles(user));
    }

    @Operation(
            summary = "Soft-delete a file",
            description = "Marks the file as deleted in the database. File is retained in Cloudflare R2.",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "204", description = "File deleted"),
                    @ApiResponse(responseCode = "404", description = "File not found"),
                    @ApiResponse(responseCode = "400", description = "Access denied")
            }
    )
    @DeleteMapping("/{fileId}")
    public ResponseEntity<Void> deleteFile(@PathVariable UUID fileId,
                                           @AuthenticationPrincipal User user) {
        fileService.deleteFile(fileId, user);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            description = "Returns a presigned GET URL for direct download from Cloudflare R2",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Download URL returned",
                            content = @Content(schema = @Schema(implementation = DownloadUrlResponse.class))),
                    @ApiResponse(responseCode = "404", description = "File not found")
            }
    )
    @GetMapping("/{fileId}/download")
    public ResponseEntity<DownloadUrlResponse> download(@PathVariable UUID fileId) {
        return ResponseEntity.ok(fileService.getDownloadUrl(fileId));
    }
}
