package com.dfs.master.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response returned when a file upload is initiated.
 * Contains the upload session ID and the chunk allocation plan.
 */
public record InitUploadResponse(
        UUID sessionId,
        UUID fileId,
        String fileName,
        long fileSize,
        int totalChunks,
        long chunkSize,
        List<ChunkAllocation> chunkAllocations,
        Instant expiresAt
) {
    /**
     * Describes where a specific chunk should be uploaded.
     */
    public record ChunkAllocation(
            int chunkIndex,
            long expectedSize,
            List<NodeTarget> targetNodes
    ) {
    }

    /**
     * A storage node targeted for chunk placement.
     */
    public record NodeTarget(
            String nodeId,
            String uploadUrl
    ) {
    }
}
