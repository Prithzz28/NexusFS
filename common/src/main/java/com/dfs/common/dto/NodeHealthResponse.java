package com.dfs.common.dto;

import com.dfs.common.enums.NodeStatus;

public record NodeHealthResponse(
        String nodeId,
        NodeStatus status,
        long totalCapacityBytes,
        long usedStorageBytes,
        long availableStorageBytes,
        long chunksStored
) {
}
