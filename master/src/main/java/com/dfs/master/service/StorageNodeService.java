package com.dfs.master.service;

import com.dfs.common.enums.NodeStatus;
import com.dfs.master.dto.NodeRegistrationRequest;
import com.dfs.master.dto.NodeResponse;
import com.dfs.master.entity.StorageNode;
import com.dfs.master.exception.DuplicateResourceException;
import com.dfs.master.exception.NodeNotFoundException;
import com.dfs.master.repository.StorageNodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Manages storage node lifecycle: registration, status tracking, and capacity queries.
 */
@Service
public class StorageNodeService {

    private static final Logger log = LoggerFactory.getLogger(StorageNodeService.class);
    private final StorageNodeRepository storageNodeRepository;

    public StorageNodeService(StorageNodeRepository storageNodeRepository) {
        this.storageNodeRepository = storageNodeRepository;
    }

    /**
     * Registers a new storage node or re-registers an existing one.
     */
    @Transactional
    public NodeResponse registerNode(NodeRegistrationRequest request) {
        StorageNode node = storageNodeRepository.findByNodeId(request.nodeId())
                .map(existing -> {
                    // Re-registration: update host, port, capacity
                    existing.setHost(request.host());
                    existing.setPort(request.port());
                    existing.setCapacity(request.capacityBytes());
                    existing.setStatus(NodeStatus.ACTIVE);
                    existing.setLastHeartbeat(Instant.now());
                    log.info("Storage node re-registered: {} ({}:{})", request.nodeId(), request.host(), request.port());
                    return existing;
                })
                .orElseGet(() -> {
                    StorageNode newNode = new StorageNode(
                            request.nodeId(), request.host(), request.port(), request.capacityBytes());
                    newNode.setLastHeartbeat(Instant.now());
                    log.info("Storage node registered: {} ({}:{})", request.nodeId(), request.host(), request.port());
                    return newNode;
                });

        node = storageNodeRepository.save(node);
        return toResponse(node);
    }

    @Transactional(readOnly = true)
    public List<NodeResponse> listNodes() {
        return storageNodeRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NodeResponse> listActiveNodes() {
        return storageNodeRepository.findByStatus(NodeStatus.ACTIVE).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public NodeResponse getNode(String nodeId) {
        StorageNode node = storageNodeRepository.findByNodeId(nodeId)
                .orElseThrow(() -> new NodeNotFoundException("Storage node not found: " + nodeId));
        return toResponse(node);
    }

    /**
     * Returns active nodes sorted by available capacity descending.
     * Used by the placement algorithm.
     */
    @Transactional(readOnly = true)
    public List<StorageNode> getActiveNodesForPlacement() {
        return storageNodeRepository.findActiveNodesOrderByAvailableCapacityDesc();
    }

    @Transactional
    public void updateNodeStatus(String nodeId, NodeStatus status) {
        StorageNode node = storageNodeRepository.findByNodeId(nodeId)
                .orElseThrow(() -> new NodeNotFoundException("Storage node not found: " + nodeId));
        node.setStatus(status);
        storageNodeRepository.save(node);
        log.info("Node {} status changed to {}", nodeId, status);
    }

    private NodeResponse toResponse(StorageNode node) {
        return new NodeResponse(
                node.getId(),
                node.getNodeId(),
                node.getHost(),
                node.getPort(),
                node.getCapacity(),
                node.getUsedStorage(),
                node.getAvailableStorage(),
                node.getStatus(),
                node.getLastHeartbeat(),
                node.getCreatedAt()
        );
    }
}
