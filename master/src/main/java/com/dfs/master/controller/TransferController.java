package com.dfs.master.controller;

import com.dfs.common.dto.ApiResponse;
import com.dfs.master.dto.CreateFileRequest;
import com.dfs.master.dto.DownloadManifest;
import com.dfs.master.dto.InitUploadResponse;
import com.dfs.master.security.DfsUserDetails;
import com.dfs.master.service.DownloadService;
import com.dfs.master.service.UploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/files")
@Tag(name = "Transfer", description = "File upload and download operations")
public class TransferController {

    private final UploadService uploadService;
    private final DownloadService downloadService;

    public TransferController(UploadService uploadService, DownloadService downloadService) {
        this.uploadService = uploadService;
        this.downloadService = downloadService;
    }

    @PostMapping("/init-upload")
    @Operation(summary = "Initiate a file upload and receive chunk allocation plan")
    public ResponseEntity<ApiResponse<InitUploadResponse>> initUpload(
            @Valid @RequestBody CreateFileRequest request,
            @AuthenticationPrincipal DfsUserDetails principal) {
        InitUploadResponse response = uploadService.initUpload(
                request.fileName(), request.fileSize(), request.contentType(), principal);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @PostMapping("/{sessionId}/chunks/{chunkIndex}")
    @Operation(summary = "Upload an individual chunk")
    public ResponseEntity<ApiResponse<Void>> uploadChunk(
            @PathVariable UUID sessionId,
            @PathVariable int chunkIndex,
            @RequestBody byte[] data,
            @RequestHeader(value = "X-Chunk-Checksum", required = false) String checksum) {
        uploadService.uploadChunk(sessionId, chunkIndex, data, checksum);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{sessionId}/complete")
    @Operation(summary = "Complete the file upload after all chunks are uploaded")
    public ResponseEntity<ApiResponse<Void>> completeUpload(
            @PathVariable UUID sessionId,
            @AuthenticationPrincipal DfsUserDetails principal) {
        uploadService.completeUpload(sessionId, principal);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/{fileId}/manifest")
    @Operation(summary = "Get the download manifest for a file")
    public ResponseEntity<ApiResponse<DownloadManifest>> getManifest(
            @PathVariable UUID fileId,
            @AuthenticationPrincipal DfsUserDetails principal) {
        DownloadManifest manifest = downloadService.getDownloadManifest(fileId, principal);
        return ResponseEntity.ok(ApiResponse.success(manifest));
    }

    @GetMapping("/{fileId}/download")
    @Operation(summary = "Download a file (streaming)")
    public void downloadFile(
            @PathVariable UUID fileId,
            @AuthenticationPrincipal DfsUserDetails principal,
            HttpServletResponse response) throws IOException {

        DownloadManifest manifest = downloadService.getDownloadManifest(fileId, principal);

        response.setContentType(manifest.contentType() != null
                ? manifest.contentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + manifest.fileName() + "\"");
        response.setHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(manifest.fileSize()));

        downloadService.streamDownload(fileId, principal, response.getOutputStream());
    }
}
