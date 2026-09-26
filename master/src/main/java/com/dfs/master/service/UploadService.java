package com.dfs.master.service;

import com.dfs.common.enums.ChunkReplicaStatus;
import com.dfs.common.enums.FileStatus;
import com.dfs.common.enums.UploadSessionStatus;
import com.dfs.master.config.DfsProperties;
import com.dfs.master.dto.InitUploadResponse;
import com.dfs.master.entity.*;
import com.dfs.master.exception.AccessDeniedException;
import com.dfs.master.exception.FileNotFoundException;
import com.dfs.master.exception.InsufficientStorageException;
import com.dfs.master.repository.*;
import com.dfs.master.security.DfsUserDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Manages the upload pipeline: session creation, chunk upload orchestration,
 * and upload finalization.
 */
@Service
public class UploadService {

    private static final Logger log = LoggerFactory.getLogger(UploadService.class);

    private final FileMetadataRepository fileMetadataRepository;
    private final ChunkRepository chunkRepository;
    private final ChunkReplicaRepository chunkReplicaRepository;
    private final UploadSessionRepository uploadSessionRepository;
    private final UserRepository userRepository;
    private final StorageNodeClient storageNodeClient;
    private final PlacementService placementService;
    private final AuditService auditService;
    private final DfsProperties dfsProperties;

    public UploadService(FileMetadataRepository fileMetadataRepository,
                         ChunkRepository chunkRepository,
                         ChunkReplicaRepository chunkReplicaRepository,
                         UploadSessionRepository uploadSessionRepository,
                         UserRepository userRepository,
                         StorageNodeClient storageNodeClient,
                         PlacementService placementService,
                         AuditService auditService,
                         DfsProperties dfsProperties) {
        this.fileMetadataRepository = fileMetadataRepository;
        this.chunkRepository = chunkRepository;
        this.chunkReplicaRepository = chunkReplicaRepository;
        this.uploadSessionRepository = uploadSessionRepository;
        this.userRepository = userRepository;
        this.storageNodeClient = storageNodeClient;
        this.placementService = placementService;
        this.auditService = auditService;
        this.dfsProperties = dfsProperties;
    }

    /**
     * Initiates a file upload: creates metadata, an upload session, and allocates chunk targets.
     */
    @Transactional
    public InitUploadResponse initUpload(String fileName, long fileSize,
                                          String contentType, DfsUserDetails principal) {
        if (fileSize > dfsProperties.maxFileSizeBytes()) {
            throw new InsufficientStorageException(
                    "File size " + fileSize + " exceeds max allowed " + dfsProperties.maxFileSizeBytes());
        }

        User owner = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new AccessDeniedException("User not found"));

        long chunkSize = dfsProperties.chunkSizeBytes();
        int totalChunks = (int) Math.ceil((double) fileSize / chunkSize);

        // Create file metadata
        FileMetadata file = new FileMetadata(owner, fileName, fileSize, contentType, totalChunks, chunkSize);
        file = fileMetadataRepository.save(file);

        // Create upload session
        Instant expiresAt = Instant.now().plus(dfsProperties.uploadSessionTimeoutMinutes(), ChronoUnit.MINUTES);
        UploadSession session = new UploadSession(owner, file, expiresAt);
        session = uploadSessionRepository.save(session);

        // Allocate chunk targets
        List<InitUploadResponse.ChunkAllocation> allocations = new ArrayList<>();
        for (int i = 0; i < totalChunks; i++) {
            long thisChunkSize = (i == totalChunks - 1)
                    ? fileSize - (long) i * chunkSize
                    : chunkSize;

            List<StorageNode> targetNodes = placementService.selectNodesForChunk(thisChunkSize);
            List<InitUploadResponse.NodeTarget> nodeTargets = targetNodes.stream()
                    .map(node -> new InitUploadResponse.NodeTarget(
                            node.getNodeId(),
                            node.getBaseUrl() + "/internal/chunks"
                    ))
                    .toList();

            allocations.add(new InitUploadResponse.ChunkAllocation(i, thisChunkSize, nodeTargets));
        }

        log.info("Upload initiated: file={} session={} chunks={}", file.getId(), session.getId(), totalChunks);

        auditService.logOperation(principal.getUserId(), "UPLOAD_INIT",
                file.getId().toString(), null, "SUCCESS",
                "Upload initiated for: " + fileName);

        return new InitUploadResponse(
                session.getId(), file.getId(), fileName, fileSize,
                totalChunks, chunkSize, allocations, expiresAt
        );
    }

    /**
     * Handles an individual chunk upload. The Master receives the chunk data and
     * distributes it to the target storage nodes.
     */
    @Transactional
    public void uploadChunk(UUID sessionId, int chunkIndex, byte[] data, String checksum) {
        UploadSession session = uploadSessionRepository.findById(sessionId)
                .orElseThrow(() -> new FileNotFoundException("Upload session not found: " + sessionId));

        if (session.isExpired()) {
            session.setStatus(UploadSessionStatus.EXPIRED);
            uploadSessionRepository.save(session);
            throw new FileNotFoundException("Upload session expired: " + sessionId);
        }

        if (session.getStatus() == UploadSessionStatus.INITIATED) {
            session.setStatus(UploadSessionStatus.IN_PROGRESS);
            uploadSessionRepository.save(session);
        }

        FileMetadata file = session.getFile();

        if (chunkIndex < 0 || chunkIndex >= file.getTotalChunks()) {
            throw new IllegalArgumentException("Invalid chunk index: " + chunkIndex);
        }

        // Create chunk record
        Chunk chunk = chunkRepository.findByFileAndChunkIndex(file, chunkIndex)
                .orElseGet(() -> {
                    Chunk newChunk = new Chunk(file, chunkIndex, data.length, checksum);
                    return chunkRepository.save(newChunk);
                });

        // Distribute to storage nodes
        List<StorageNode> targetNodes = placementService.selectNodesForChunk(data.length);
        for (StorageNode node : targetNodes) {
            String nodeChecksum = storageNodeClient.storeChunk(node, chunk.getId().toString(), data);
            ChunkReplica replica = new ChunkReplica(chunk, node);
            if (nodeChecksum != null) {
                replica.setStatus(ChunkReplicaStatus.ACTIVE);
                replica.setLastVerifiedAt(Instant.now());
            } else {
                replica.setStatus(ChunkReplicaStatus.PENDING);
            }
            chunkReplicaRepository.save(replica);
        }

        log.debug("Chunk {}/{} uploaded for file {}", chunkIndex, file.getTotalChunks(), file.getId());
    }

    /**
     * Finalizes an upload: verifies all chunks are present and sets file status to ACTIVE.
     */
    @Transactional
    public void completeUpload(UUID sessionId, DfsUserDetails principal) {
        UploadSession session = uploadSessionRepository.findById(sessionId)
                .orElseThrow(() -> new FileNotFoundException("Upload session not found: " + sessionId));

        FileMetadata file = session.getFile();
        long uploadedChunks = chunkRepository.countByFile(file);

        if (uploadedChunks < file.getTotalChunks()) {
            throw new IllegalStateException(
                    String.format("Upload incomplete: %d/%d chunks uploaded",
                            uploadedChunks, file.getTotalChunks()));
        }

        file.setStatus(FileStatus.ACTIVE);
        fileMetadataRepository.save(file);

        session.setStatus(UploadSessionStatus.COMPLETED);
        uploadSessionRepository.save(session);

        log.info("Upload completed: file={} ({})", file.getId(), file.getFileName());

        auditService.logOperation(principal.getUserId(), "UPLOAD_COMPLETE",
                file.getId().toString(), null, "SUCCESS",
                "Upload completed for: " + file.getFileName());
    }
}
