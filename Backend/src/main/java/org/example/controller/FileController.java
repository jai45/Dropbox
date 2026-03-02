package org.example.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.dto.DownloadUrlResponse;
import org.example.dto.PresignRequest;
import org.example.dto.PresignResponse;
import org.example.service.FileService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
            summary = "Get presigned download URL",
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
