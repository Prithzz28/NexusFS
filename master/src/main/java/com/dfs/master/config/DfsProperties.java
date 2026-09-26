package com.dfs.master.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dfs")
public record DfsProperties(
        long chunkSizeBytes,
        int replicationFactor,
        int heartbeatIntervalSeconds,
        int nodeFailureTimeoutSeconds,
        long maxFileSizeBytes,
        int uploadSessionTimeoutMinutes
) {
}
