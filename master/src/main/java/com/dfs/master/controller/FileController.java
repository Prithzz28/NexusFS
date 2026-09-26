package com.dfs.master.controller;

import com.dfs.common.dto.ApiResponse;
import com.dfs.master.dto.CreateFileRequest;
import com.dfs.master.dto.FileDetailResponse;
import com.dfs.master.dto.FileListResponse;
import com.dfs.master.dto.FileResponse;
import com.dfs.master.security.DfsUserDetails;
import com.dfs.master.service.FileMetadataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/files")
@Tag(name = "Files", description = "File metadata CRUD operations")
public class FileController {

    private final FileMetadataService fileMetadataService;

    public FileController(FileMetadataService fileMetadataService) {
        this.fileMetadataService = fileMetadataService;
    }

    @PostMapping
    @Operation(summary = "Register a new file and prepare for upload")
    public ResponseEntity<ApiResponse<FileDetailResponse>> createFile(
            @Valid @RequestBody CreateFileRequest request,
            @AuthenticationPrincipal DfsUserDetails principal) {
        FileDetailResponse response = fileMetadataService.createFile(request, principal);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @GetMapping
    @Operation(summary = "List all files owned by the authenticated user")
    public ResponseEntity<ApiResponse<FileListResponse>> listFiles(
            @AuthenticationPrincipal DfsUserDetails principal,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        Page<FileResponse> files = fileMetadataService.listUserFiles(principal, pageable);
        return ResponseEntity.ok(ApiResponse.success(FileListResponse.fromPage(files)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get detailed metadata for a specific file")
    public ResponseEntity<ApiResponse<FileDetailResponse>> getFile(
            @PathVariable UUID id,
            @AuthenticationPrincipal DfsUserDetails principal) {
        FileDetailResponse response = fileMetadataService.getFileDetails(id, principal);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/search")
    @Operation(summary = "Search files by filename")
    public ResponseEntity<ApiResponse<FileListResponse>> searchFiles(
            @RequestParam String query,
            @AuthenticationPrincipal DfsUserDetails principal,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        Page<FileResponse> results = fileMetadataService.searchFiles(principal, query, pageable);
        return ResponseEntity.ok(ApiResponse.success(FileListResponse.fromPage(results)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft-delete a file")
    public ResponseEntity<ApiResponse<Void>> deleteFile(
            @PathVariable UUID id,
            @AuthenticationPrincipal DfsUserDetails principal) {
        fileMetadataService.softDeleteFile(id, principal);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{id}/restore")
    @Operation(summary = "Restore a soft-deleted file")
    public ResponseEntity<ApiResponse<FileDetailResponse>> restoreFile(
            @PathVariable UUID id,
            @AuthenticationPrincipal DfsUserDetails principal) {
        FileDetailResponse response = fileMetadataService.restoreFile(id, principal);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/{id}/rename")
    @Operation(summary = "Rename a file")
    public ResponseEntity<ApiResponse<FileDetailResponse>> renameFile(
            @PathVariable UUID id,
            @RequestParam String newName,
            @AuthenticationPrincipal DfsUserDetails principal) {
        FileDetailResponse response = fileMetadataService.renameFile(id, newName, principal);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
