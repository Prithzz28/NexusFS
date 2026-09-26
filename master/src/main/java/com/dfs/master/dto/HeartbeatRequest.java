package com.dfs.master.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Heartbeat payload sent by storage nodes to the Master.
 */
public record HeartbeatRequest(
        @NotBlank(message = "Node ID is required")
        String nodeId,

        @PositiveOrZero(message = "Used storage must be non-negative")
        long usedStorageBytes,

        @PositiveOrZero(message = "Available storage must be non-negative")
        long availableStorageBytes,

        @PositiveOrZero(message = "Chunks stored must be non-negative")
        long chunksStored
) {
}
