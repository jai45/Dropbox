package org.example.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.dto.PresignRequest;
import org.example.dto.PresignResponse;
import org.example.service.FileService;
import org.springframework.http.HttpHeaders;
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
            responses = {
                    @ApiResponse(responseCode = "201", description = "Presigned URL generated",
                            content = @Content(schema = @Schema(implementation = PresignResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Owner user not found")
            }
    )
    @PostMapping("/presign")
    public ResponseEntity<PresignResponse> presign(@RequestBody PresignRequest request) {
        PresignResponse response = fileService.createPresignedUpload(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
            summary = "Download file via redirect",
            description = "Generates a presigned GET URL for the file and redirects the client directly to Cloudflare R2",
            responses = {
                    @ApiResponse(responseCode = "302", description = "Redirect to presigned R2 download URL"),
                    @ApiResponse(responseCode = "404", description = "File not found")
            }
    )
    @GetMapping("/{fileId}")
    public ResponseEntity<Void> download(@PathVariable UUID fileId) {
        String downloadUrl = fileService.getDownloadUrl(fileId);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, downloadUrl)
                .build();
    }
}
