package com.dfs.master.service;

import com.dfs.common.enums.FileStatus;
import com.dfs.master.config.DfsProperties;
import com.dfs.master.dto.CreateFileRequest;
import com.dfs.master.dto.FileDetailResponse;
import com.dfs.master.dto.FileResponse;
import com.dfs.master.entity.FileMetadata;
import com.dfs.master.entity.User;
import com.dfs.master.exception.AccessDeniedException;
import com.dfs.master.exception.FileNotFoundException;
import com.dfs.master.repository.FileMetadataRepository;
import com.dfs.master.repository.UserRepository;
import com.dfs.master.security.DfsUserDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Core service for file metadata management.
 * Handles file registration, queries, soft-deletion, and restore operations.
 * All operations include ownership authorization checks.
 */
@Service
public class FileMetadataService {

    private static final Logger log = LoggerFactory.getLogger(FileMetadataService.class);

    private final FileMetadataRepository fileMetadataRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final DfsProperties dfsProperties;

    public FileMetadataService(FileMetadataRepository fileMetadataRepository,
                               UserRepository userRepository,
                               AuditService auditService,
                               DfsProperties dfsProperties) {
        this.fileMetadataRepository = fileMetadataRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.dfsProperties = dfsProperties;
    }

    /**
     * Registers new file metadata. Computes chunk count based on configured chunk size.
     * Sets initial status to UPLOADING.
     */
    @Transactional
    public FileDetailResponse createFile(CreateFileRequest request, DfsUserDetails principal) {
        User owner = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new AccessDeniedException("User not found"));

        long chunkSizeBytes = dfsProperties.chunkSizeBytes();
        int totalChunks = (int) Math.ceil((double) request.fileSize() / chunkSizeBytes);

        FileMetadata file = new FileMetadata(
                owner,
                request.fileName(),
                request.fileSize(),
                request.contentType(),
                totalChunks,
                chunkSizeBytes
        );

        file = fileMetadataRepository.save(file);
        log.info("File registered: {} (id={}, size={}, chunks={})",
                file.getFileName(), file.getId(), file.getFileSize(), file.getTotalChunks());

        auditService.logOperation(principal.getUserId(), "FILE_CREATE",
                file.getId().toString(), null, "SUCCESS",
                "Created file: " + file.getFileName());

        return toDetailResponse(file);
    }

    /**
     * Returns a paginated list of the user's files (excluding soft-deleted).
     */
    @Transactional(readOnly = true)
    public Page<FileResponse> listUserFiles(DfsUserDetails principal, Pageable pageable) {
        User owner = userRepository.getReferenceById(principal.getUserId());
        return fileMetadataRepository.findByOwnerAndStatusNot(owner, FileStatus.DELETED, pageable)
                .map(this::toResponse);
    }

    /**
     * Returns detailed metadata for a specific file, with ownership check.
     */
    @Transactional(readOnly = true)
    public FileDetailResponse getFileDetails(UUID fileId, DfsUserDetails principal) {
        FileMetadata file = findFileAndCheckOwnership(fileId, principal);
        return toDetailResponse(file);
    }

    /**
     * Searches user's files by filename substring (case-insensitive).
     */
    @Transactional(readOnly = true)
    public Page<FileResponse> searchFiles(DfsUserDetails principal, String query, Pageable pageable) {
        User owner = userRepository.getReferenceById(principal.getUserId());
        return fileMetadataRepository.searchByOwnerAndFileName(owner, query, pageable)
                .map(this::toResponse);
    }

    /**
     * Soft-deletes a file by setting status to DELETED.
     * The actual chunk cleanup is handled asynchronously by a background process.
     */
    @Transactional
    public void softDeleteFile(UUID fileId, DfsUserDetails principal) {
        FileMetadata file = findFileAndCheckOwnership(fileId, principal);

        if (file.getStatus() == FileStatus.DELETED) {
            throw new FileNotFoundException("File already deleted: " + fileId);
        }

        file.setStatus(FileStatus.DELETING);
        fileMetadataRepository.save(file);
        log.info("File soft-deleted: {} (id={})", file.getFileName(), file.getId());

        auditService.logOperation(principal.getUserId(), "FILE_DELETE",
                file.getId().toString(), null, "SUCCESS",
                "Soft-deleted file: " + file.getFileName());
    }

    /**
     * Restores a soft-deleted file back to ACTIVE status.
     */
    @Transactional
    public FileDetailResponse restoreFile(UUID fileId, DfsUserDetails principal) {
        FileMetadata file = findFileAndCheckOwnership(fileId, principal);

        if (file.getStatus() != FileStatus.DELETING && file.getStatus() != FileStatus.DELETED) {
            throw new FileNotFoundException("File is not in a deleted state: " + fileId);
        }

        file.setStatus(FileStatus.ACTIVE);
        file = fileMetadataRepository.save(file);
        log.info("File restored: {} (id={})", file.getFileName(), file.getId());

        auditService.logOperation(principal.getUserId(), "FILE_RESTORE",
                file.getId().toString(), null, "SUCCESS",
                "Restored file: " + file.getFileName());

        return toDetailResponse(file);
    }

    /**
     * Renames a file.
     */
    @Transactional
    public FileDetailResponse renameFile(UUID fileId, String newName, DfsUserDetails principal) {
        FileMetadata file = findFileAndCheckOwnership(fileId, principal);
        String oldName = file.getFileName();
        file.setFileName(newName);
        file = fileMetadataRepository.save(file);
        log.info("File renamed: {} -> {} (id={})", oldName, newName, file.getId());

        auditService.logOperation(principal.getUserId(), "FILE_RENAME",
                file.getId().toString(), null, "SUCCESS",
                "Renamed: " + oldName + " -> " + newName);

        return toDetailResponse(file);
    }

    // ── Internal helpers ────────────────────────────────────────

    private FileMetadata findFileAndCheckOwnership(UUID fileId, DfsUserDetails principal) {
        User owner = userRepository.getReferenceById(principal.getUserId());
        return fileMetadataRepository.findByIdAndOwner(fileId, owner)
                .orElseThrow(() -> new FileNotFoundException("File not found: " + fileId));
    }

    private FileResponse toResponse(FileMetadata file) {
        return new FileResponse(
                file.getId(),
                file.getFileName(),
                file.getFileSize(),
                file.getContentType(),
                file.getStatus(),
                file.getTotalChunks(),
                file.getCreatedAt(),
                file.getUpdatedAt()
        );
    }

    private FileDetailResponse toDetailResponse(FileMetadata file) {
        return new FileDetailResponse(
                file.getId(),
                file.getFileName(),
                file.getFileSize(),
                file.getContentType(),
                file.getStatus(),
                file.getTotalChunks(),
                file.getChunkSize(),
                file.getChecksum(),
                file.getOwner().getUsername(),
                file.getCreatedAt(),
                file.getUpdatedAt()
        );
    }
}
