package com.dfs.master.service;

import com.dfs.common.enums.ChunkReplicaStatus;
import com.dfs.common.enums.FileStatus;
import com.dfs.master.dto.DownloadManifest;
import com.dfs.master.entity.*;
import com.dfs.master.exception.AccessDeniedException;
import com.dfs.master.exception.FileNotFoundException;
import com.dfs.master.repository.*;
import com.dfs.master.security.DfsUserDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.util.List;
import java.util.UUID;

/**
 * Manages the download pipeline: chunk manifest generation and streaming assembly.
 */
@Service
public class DownloadService {

    private static final Logger log = LoggerFactory.getLogger(DownloadService.class);

    private final FileMetadataRepository fileMetadataRepository;
    private final ChunkRepository chunkRepository;
    private final ChunkReplicaRepository chunkReplicaRepository;
    private final UserRepository userRepository;
    private final StorageNodeClient storageNodeClient;
    private final AuditService auditService;

    public DownloadService(FileMetadataRepository fileMetadataRepository,
                           ChunkRepository chunkRepository,
                           ChunkReplicaRepository chunkReplicaRepository,
                           UserRepository userRepository,
                           StorageNodeClient storageNodeClient,
                           AuditService auditService) {
        this.fileMetadataRepository = fileMetadataRepository;
        this.chunkRepository = chunkRepository;
        this.chunkReplicaRepository = chunkReplicaRepository;
        this.userRepository = userRepository;
        this.storageNodeClient = storageNodeClient;
        this.auditService = auditService;
    }

    /**
     * Generates a download manifest describing where each chunk can be retrieved.
     */
    @Transactional(readOnly = true)
    public DownloadManifest getDownloadManifest(UUID fileId, DfsUserDetails principal) {
        User owner = userRepository.getReferenceById(principal.getUserId());
        FileMetadata file = fileMetadataRepository.findByIdAndOwner(fileId, owner)
                .orElseThrow(() -> new FileNotFoundException("File not found: " + fileId));

        if (file.getStatus() != FileStatus.ACTIVE) {
            throw new FileNotFoundException("File is not available for download: " + fileId);
        }

        List<Chunk> chunks = chunkRepository.findByFileOrderByChunkIndexAsc(file);
        List<DownloadManifest.ChunkLocation> chunkLocations = chunks.stream()
                .map(chunk -> {
                    List<ChunkReplica> activeReplicas =
                            chunkReplicaRepository.findByChunkAndStatus(chunk, ChunkReplicaStatus.ACTIVE);

                    List<DownloadManifest.NodeSource> sources = activeReplicas.stream()
                            .map(replica -> new DownloadManifest.NodeSource(
                                    replica.getStorageNode().getNodeId(),
                                    replica.getStorageNode().getBaseUrl() + "/internal/chunks/" + chunk.getId()
                            ))
                            .toList();

                    return new DownloadManifest.ChunkLocation(
                            chunk.getChunkIndex(),
                            chunk.getId(),
                            chunk.getChunkSize(),
                            chunk.getChecksum(),
                            sources
                    );
                })
                .toList();

        return new DownloadManifest(
                file.getId(), file.getFileName(), file.getFileSize(),
                file.getContentType(), file.getTotalChunks(),
                file.getChecksum(), chunkLocations
        );
    }

    /**
     * Streams the full file by reading chunks in order from storage nodes.
     * Writes directly to the output stream without loading the entire file in memory.
     */
    @Transactional(readOnly = true)
    public void streamDownload(UUID fileId, DfsUserDetails principal, OutputStream outputStream) {
        DownloadManifest manifest = getDownloadManifest(fileId, principal);

        for (DownloadManifest.ChunkLocation chunkLoc : manifest.chunks()) {
            byte[] chunkData = downloadChunkWithFallback(chunkLoc);
            if (chunkData == null) {
                throw new FileNotFoundException(
                        "Failed to download chunk " + chunkLoc.chunkIndex() + " of file " + fileId);
            }
            try {
                outputStream.write(chunkData);
            } catch (IOException e) {
                throw new RuntimeException("Failed to write chunk to output stream", e);
            }
        }

        try {
            outputStream.flush();
        } catch (IOException e) {
            throw new RuntimeException("Failed to flush output stream", e);
        }

        auditService.logOperation(principal.getUserId(), "FILE_DOWNLOAD",
                fileId.toString(), null, "SUCCESS",
                "Downloaded file: " + manifest.fileName());
    }

    /**
     * Attempts to download a chunk from any available source node.
     * Falls back to alternative replicas if the primary source fails.
     */
    private byte[] downloadChunkWithFallback(DownloadManifest.ChunkLocation chunkLoc) {
        for (DownloadManifest.NodeSource source : chunkLoc.sources()) {
            try {
                // We need the StorageNode entity for the client, but we only have nodeId
                // Use the download URL directly via RestTemplate
                byte[] data = downloadFromUrl(source.downloadUrl());
                if (data != null && data.length > 0) {
                    log.debug("Downloaded chunk {} from node {}", chunkLoc.chunkIndex(), source.nodeId());
                    return data;
                }
            } catch (Exception e) {
                log.warn("Failed to download chunk {} from node {}: {}",
                        chunkLoc.chunkIndex(), source.nodeId(), e.getMessage());
            }
        }
        return null;
    }

    private byte[] downloadFromUrl(String url) {
        try {
            org.springframework.web.client.RestTemplate rt = new org.springframework.web.client.RestTemplate();
            return rt.getForObject(url, byte[].class);
        } catch (Exception e) {
            log.debug("Failed to download from {}: {}", url, e.getMessage());
            return null;
        }
    }
}
