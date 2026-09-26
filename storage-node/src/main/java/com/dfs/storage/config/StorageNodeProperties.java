package com.dfs.storage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "storage")
public record StorageNodeProperties(
        String nodeId,
        String storageRoot,
        long capacityBytes,
        String masterUrl,
        int heartbeatIntervalSeconds
) {
}
