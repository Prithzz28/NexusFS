package com.dfs.master.config;

import com.dfs.common.enums.NodeStatus;
import com.dfs.master.repository.ChunkReplicaRepository;
import com.dfs.master.repository.FileMetadataRepository;
import com.dfs.master.repository.StorageNodeRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Configuration;

/**
 * Custom Micrometer metrics for DFS observability.
 * Exposes cluster-level gauges and operation timers for Prometheus scraping.
 */
@Configuration
public class MetricsConfig {

    public MetricsConfig(MeterRegistry meterRegistry,
                         StorageNodeRepository storageNodeRepository,
                         FileMetadataRepository fileMetadataRepository,
                         DfsProperties dfsProperties) {

        // Active node count
        Gauge.builder("dfs.nodes.active", storageNodeRepository,
                        repo -> repo.countByStatus(NodeStatus.ACTIVE))
                .description("Number of active storage nodes")
                .register(meterRegistry);

        // Offline node count
        Gauge.builder("dfs.nodes.offline", storageNodeRepository,
                        repo -> repo.countByStatus(NodeStatus.OFFLINE))
                .description("Number of offline storage nodes")
                .register(meterRegistry);

        // Total cluster capacity (sum of all active node capacities)
        Gauge.builder("dfs.storage.total_bytes", storageNodeRepository,
                        repo -> repo.findByStatus(NodeStatus.ACTIVE).stream()
                                .mapToLong(n -> n.getCapacity()).sum())
                .description("Total cluster storage capacity in bytes")
                .baseUnit("bytes")
                .register(meterRegistry);

        // Used cluster storage
        Gauge.builder("dfs.storage.used_bytes", storageNodeRepository,
                        repo -> repo.findByStatus(NodeStatus.ACTIVE).stream()
                                .mapToLong(n -> n.getUsedStorage()).sum())
                .description("Total used storage across all active nodes")
                .baseUnit("bytes")
                .register(meterRegistry);

        // Replication factor setting
        Gauge.builder("dfs.config.replication_factor", dfsProperties,
                        props -> props.replicationFactor())
                .description("Configured replication factor")
                .register(meterRegistry);

        // Upload latency timer
        Timer.builder("dfs.upload.duration")
                .description("File upload duration")
                .register(meterRegistry);

        // Download latency timer
        Timer.builder("dfs.download.duration")
                .description("File download duration")
                .register(meterRegistry);
    }
}
