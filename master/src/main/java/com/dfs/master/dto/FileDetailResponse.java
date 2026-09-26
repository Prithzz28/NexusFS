package com.dfs.master.dto;

import com.dfs.common.enums.FileStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Detailed file metadata response including checksum and chunk info.
 */
public record FileDetailResponse(
        UUID id,
        String fileName,
        long fileSize,
        String contentType,
        FileStatus status,
        int totalChunks,
        long chunkSize,
        String checksum,
        String ownerUsername,
        Instant createdAt,
        Instant updatedAt
) {
}
