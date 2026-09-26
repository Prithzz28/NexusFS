package com.dfs.master.scheduler;

import com.dfs.common.enums.NodeStatus;
import com.dfs.master.config.DfsProperties;
import com.dfs.master.entity.StorageNode;
import com.dfs.master.repository.StorageNodeRepository;
import com.dfs.master.service.ReplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Background scheduler that monitors storage node health and triggers
 * self-healing re-replication for under-replicated chunks.
 */
@Component
public class NodeMonitorScheduler {

    private static final Logger log = LoggerFactory.getLogger(NodeMonitorScheduler.class);

    private final StorageNodeRepository storageNodeRepository;
    private final ReplicationService replicationService;
    private final DfsProperties dfsProperties;

    public NodeMonitorScheduler(StorageNodeRepository storageNodeRepository,
                                 ReplicationService replicationService,
                                 DfsProperties dfsProperties) {
        this.storageNodeRepository = storageNodeRepository;
        this.replicationService = replicationService;
        this.dfsProperties = dfsProperties;
    }

    /**
     * Detects nodes that have missed heartbeats beyond the configured timeout
     * and marks them as OFFLINE.
     * Runs every 10 seconds.
     */
    @Scheduled(fixedDelayString = "${dfs.heartbeat-interval-seconds:5}000",
               initialDelayString = "${dfs.heartbeat-interval-seconds:5}000")
    @Transactional
    public void detectFailedNodes() {
        Instant threshold = Instant.now().minus(
                dfsProperties.nodeFailureTimeoutSeconds(), ChronoUnit.SECONDS);

        List<StorageNode> staleNodes = storageNodeRepository.findStaleNodes(
                NodeStatus.ACTIVE, threshold);

        for (StorageNode node : staleNodes) {
            log.warn("Node {} missed heartbeat (last seen: {}). Marking OFFLINE.",
                    node.getNodeId(), node.getLastHeartbeat());
            node.setStatus(NodeStatus.OFFLINE);
            storageNodeRepository.save(node);
        }

        if (!staleNodes.isEmpty()) {
            log.info("Marked {} nodes as OFFLINE. Triggering re-replication scan.",
                    staleNodes.size());
        }
    }

    /**
     * Scans for under-replicated chunks and triggers re-replication.
     * Runs every 30 seconds.
     */
    @Scheduled(fixedDelay = 30000, initialDelay = 60000)
    public void selfHealingReplication() {
        try {
            int count = replicationService.replicateUnderReplicatedChunks();
            if (count > 0) {
                log.info("Self-healing: replicated {} under-replicated chunks", count);
            }
        } catch (Exception e) {
            log.error("Self-healing replication scan failed: {}", e.getMessage());
        }
    }
}
