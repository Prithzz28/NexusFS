package com.dfs.master.service;

import com.dfs.common.enums.NodeStatus;
import com.dfs.master.dto.HeartbeatRequest;
import com.dfs.master.entity.StorageNode;
import com.dfs.master.repository.StorageNodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Processes heartbeat signals from storage nodes.
 * Updates node status, storage metrics, and last heartbeat timestamp.
 */
@Service
public class HeartbeatService {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatService.class);
    private final StorageNodeRepository storageNodeRepository;

    public HeartbeatService(StorageNodeRepository storageNodeRepository) {
        this.storageNodeRepository = storageNodeRepository;
    }

    @Transactional
    public void processHeartbeat(HeartbeatRequest request) {
        StorageNode node = storageNodeRepository.findByNodeId(request.nodeId())
                .orElse(null);

        if (node == null) {
            log.warn("Heartbeat from unknown node: {}. Ignoring.", request.nodeId());
            return;
        }

        node.setLastHeartbeat(Instant.now());
        node.setUsedStorage(request.usedStorageBytes());

        // If node was marked OFFLINE or DEGRADED, bring it back to ACTIVE
        if (node.getStatus() == NodeStatus.OFFLINE || node.getStatus() == NodeStatus.DEGRADED) {
            log.info("Node {} came back online (was {})", request.nodeId(), node.getStatus());
            node.setStatus(NodeStatus.ACTIVE);
        }

        storageNodeRepository.save(node);
        log.trace("Heartbeat processed: node={} used={}B available={}B chunks={}",
                request.nodeId(), request.usedStorageBytes(),
                request.availableStorageBytes(), request.chunksStored());
    }
}
