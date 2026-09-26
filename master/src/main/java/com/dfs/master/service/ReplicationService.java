package com.dfs.master.service;

import com.dfs.common.constants.StorageConstants;
import com.dfs.common.enums.ChunkReplicaStatus;
import com.dfs.master.config.DfsProperties;
import com.dfs.master.entity.Chunk;
import com.dfs.master.entity.ChunkReplica;
import com.dfs.master.entity.StorageNode;
import com.dfs.master.repository.ChunkReplicaRepository;
import com.dfs.master.repository.ChunkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Handles chunk replication to maintain the target replication factor.
 * Orchestrates data transfer from existing replicas to new target nodes.
 */
@Service
public class ReplicationService {

    private static final Logger log = LoggerFactory.getLogger(ReplicationService.class);

    private final ChunkRepository chunkRepository;
    private final ChunkReplicaRepository chunkReplicaRepository;
    private final StorageNodeClient storageNodeClient;
    private final PlacementService placementService;
    private final DfsProperties dfsProperties;

    public ReplicationService(ChunkRepository chunkRepository,
                              ChunkReplicaRepository chunkReplicaRepository,
                              StorageNodeClient storageNodeClient,
                              PlacementService placementService,
                              DfsProperties dfsProperties) {
        this.chunkRepository = chunkRepository;
        this.chunkReplicaRepository = chunkReplicaRepository;
        this.storageNodeClient = storageNodeClient;
        this.placementService = placementService;
        this.dfsProperties = dfsProperties;
    }

    /**
     * Checks for under-replicated chunks and triggers re-replication.
     * Called by the self-healing scheduler.
     */
    @Transactional
    public int replicateUnderReplicatedChunks() {
        int targetFactor = dfsProperties.replicationFactor();
        List<Object[]> underReplicated =
                chunkReplicaRepository.findUnderReplicatedChunkIds(targetFactor);

        if (underReplicated.isEmpty()) {
            return 0;
        }

        int replicatedCount = 0;
        for (Object[] row : underReplicated) {
            UUID chunkId = (UUID) row[0];
            long currentReplicas = (Long) row[1];
            int needed = targetFactor - (int) currentReplicas;

            try {
                boolean success = replicateChunk(chunkId, needed);
                if (success) {
                    replicatedCount++;
                }
            } catch (Exception e) {
                log.error("Failed to replicate chunk {}: {}", chunkId, e.getMessage());
            }
        }

        log.info("Replication scan complete: {}/{} under-replicated chunks handled",
                replicatedCount, underReplicated.size());
        return replicatedCount;
    }

    /**
     * Replicates a specific chunk to additional nodes.
     */
    private boolean replicateChunk(UUID chunkId, int additionalReplicas) {
        Chunk chunk = chunkRepository.findById(chunkId).orElse(null);
        if (chunk == null) {
            log.warn("Chunk {} not found for replication", chunkId);
            return false;
        }

        // Find existing active replicas to read data from
        List<ChunkReplica> activeReplicas =
                chunkReplicaRepository.findByChunkAndStatus(chunk, ChunkReplicaStatus.ACTIVE);
        if (activeReplicas.isEmpty()) {
            log.error("No active replicas found for chunk {} - data may be lost!", chunkId);
            return false;
        }

        // Get existing node IDs to exclude from placement
        List<String> existingNodeIds = chunkReplicaRepository.findByChunk(chunk).stream()
                .map(r -> r.getStorageNode().getNodeId())
                .toList();

        // Select new target nodes
        List<StorageNode> newTargets = placementService.selectNodesForReplication(
                chunk.getChunkSize(), existingNodeIds, additionalReplicas);

        if (newTargets.isEmpty()) {
            log.warn("No suitable nodes available for replicating chunk {}", chunkId);
            return false;
        }

        // Read chunk data from an existing replica
        byte[] chunkData = null;
        for (ChunkReplica sourceReplica : activeReplicas) {
            chunkData = storageNodeClient.readChunk(
                    sourceReplica.getStorageNode(), chunkId.toString());
            if (chunkData != null) {
                break;
            }
        }

        if (chunkData == null) {
            log.error("Failed to read chunk {} from any active replica", chunkId);
            return false;
        }

        // Store on new target nodes
        boolean anySuccess = false;
        for (StorageNode target : newTargets) {
            String checksum = storageNodeClient.storeChunk(target, chunkId.toString(), chunkData);
            ChunkReplica newReplica = new ChunkReplica(chunk, target);
            if (checksum != null) {
                // Verify checksum matches
                if (chunk.getChecksum() != null && !chunk.getChecksum().equals(checksum)) {
                    log.error("Checksum mismatch for chunk {} on node {}", chunkId, target.getNodeId());
                    newReplica.setStatus(ChunkReplicaStatus.CORRUPTED);
                } else {
                    newReplica.setStatus(ChunkReplicaStatus.ACTIVE);
                    newReplica.setLastVerifiedAt(Instant.now());
                    anySuccess = true;
                }
            } else {
                newReplica.setStatus(ChunkReplicaStatus.PENDING);
            }
            chunkReplicaRepository.save(newReplica);
        }

        if (anySuccess) {
            log.info("Replicated chunk {} to {} new nodes", chunkId, newTargets.size());
        }
        return anySuccess;
    }

    /**
     * Validates the integrity of a specific chunk replica by comparing checksums.
     */
    @Transactional
    public boolean verifyReplica(ChunkReplica replica) {
        String storedChecksum = storageNodeClient.verifyChunk(
                replica.getStorageNode(), replica.getChunk().getId().toString());

        if (storedChecksum == null) {
            replica.setStatus(ChunkReplicaStatus.CORRUPTED);
            chunkReplicaRepository.save(replica);
            log.warn("Replica {} on node {} is missing or corrupted",
                    replica.getChunk().getId(), replica.getStorageNode().getNodeId());
            return false;
        }

        if (replica.getChunk().getChecksum() != null &&
                !replica.getChunk().getChecksum().equals(storedChecksum)) {
            replica.setStatus(ChunkReplicaStatus.CORRUPTED);
            chunkReplicaRepository.save(replica);
            log.warn("Checksum mismatch for replica {} on node {}",
                    replica.getChunk().getId(), replica.getStorageNode().getNodeId());
            return false;
        }

        replica.setLastVerifiedAt(Instant.now());
        chunkReplicaRepository.save(replica);
        return true;
    }
}
