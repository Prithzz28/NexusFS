package com.dfs.master.dto;

import com.dfs.common.enums.NodeStatus;

import java.time.Instant;
import java.util.UUID;

public record NodeResponse(
        UUID id,
        String nodeId,
        String host,
        int port,
        long capacityBytes,
        long usedStorageBytes,
        long availableStorageBytes,
        NodeStatus status,
        Instant lastHeartbeat,
        Instant createdAt
) {
}
