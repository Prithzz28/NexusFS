package com.dfs.master.dto;

import com.dfs.common.enums.FileStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Compact file summary for list views.
 */
public record FileResponse(
        UUID id,
        String fileName,
        long fileSize,
        String contentType,
        FileStatus status,
        int totalChunks,
        Instant createdAt,
        Instant updatedAt
) {
}
