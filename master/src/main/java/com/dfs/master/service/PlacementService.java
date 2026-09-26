package com.dfs.master.service;

import com.dfs.master.config.DfsProperties;
import com.dfs.master.entity.StorageNode;
import com.dfs.master.exception.InsufficientStorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Placement algorithm for selecting storage nodes for chunk storage.
 * Considers available disk capacity and spreads replicas across different nodes.
 */
@Service
public class PlacementService {

    private static final Logger log = LoggerFactory.getLogger(PlacementService.class);
    private final StorageNodeService storageNodeService;
    private final DfsProperties dfsProperties;

    public PlacementService(StorageNodeService storageNodeService, DfsProperties dfsProperties) {
        this.storageNodeService = storageNodeService;
        this.dfsProperties = dfsProperties;
    }

    /**
     * Selects target nodes for chunk placement.
     * Returns a list of nodes where the chunk should be stored (size = replication factor).
     *
     * Strategy: Pick nodes with the most available capacity, skipping nodes
     * that don't have enough space for the chunk.
     */
    public List<StorageNode> selectNodesForChunk(long chunkSize) {
        int replicationFactor = dfsProperties.replicationFactor();
        List<StorageNode> availableNodes = storageNodeService.getActiveNodesForPlacement();

        if (availableNodes.size() < replicationFactor) {
            throw new InsufficientStorageException(
                    String.format("Need %d active nodes for replication but only %d available",
                            replicationFactor, availableNodes.size()));
        }

        List<StorageNode> selectedNodes = new ArrayList<>();
        for (StorageNode node : availableNodes) {
            if (selectedNodes.size() >= replicationFactor) {
                break;
            }
            if (node.getAvailableStorage() >= chunkSize) {
                selectedNodes.add(node);
            }
        }

        if (selectedNodes.size() < replicationFactor) {
            throw new InsufficientStorageException(
                    String.format("Need %d nodes with %d bytes free but only %d qualify",
                            replicationFactor, chunkSize, selectedNodes.size()));
        }

        log.debug("Selected {} nodes for chunk placement: {}", selectedNodes.size(),
                selectedNodes.stream().map(StorageNode::getNodeId).toList());
        return selectedNodes;
    }

    /**
     * Selects nodes for re-replication of under-replicated chunks.
     * Excludes nodes that already hold a replica.
     */
    public List<StorageNode> selectNodesForReplication(long chunkSize,
                                                        List<String> excludeNodeIds,
                                                        int count) {
        List<StorageNode> availableNodes = storageNodeService.getActiveNodesForPlacement();
        List<StorageNode> selectedNodes = new ArrayList<>();

        for (StorageNode node : availableNodes) {
            if (selectedNodes.size() >= count) {
                break;
            }
            if (!excludeNodeIds.contains(node.getNodeId()) &&
                    node.getAvailableStorage() >= chunkSize) {
                selectedNodes.add(node);
            }
        }

        return selectedNodes;
    }
}
