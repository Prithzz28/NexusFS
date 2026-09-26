package com.dfs.master.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record NodeRegistrationRequest(
        @NotBlank(message = "Node ID is required")
        String nodeId,

        @NotBlank(message = "Host is required")
        String host,

        @Positive(message = "Port must be positive")
        int port,

        @Positive(message = "Capacity must be positive")
        long capacityBytes
) {
}
