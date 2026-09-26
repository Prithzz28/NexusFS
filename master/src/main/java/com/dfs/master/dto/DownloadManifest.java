package com.dfs.master.dto;

import java.util.List;
import java.util.UUID;

/**
 * Response for download requests, containing the chunk manifest.
 */
public record DownloadManifest(
        UUID fileId,
        String fileName,
        long fileSize,
        String contentType,
        int totalChunks,
        String checksum,
        List<ChunkLocation> chunks
) {
    /**
     * Describes where each chunk can be downloaded from.
     */
    public record ChunkLocation(
            int chunkIndex,
            UUID chunkId,
            long chunkSize,
            String checksum,
            List<NodeSource> sources
    ) {
    }

    /**
     * A source node that holds a copy of a chunk.
     */
    public record NodeSource(
            String nodeId,
            String downloadUrl
    ) {
    }
}
