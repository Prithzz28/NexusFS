package com.dfs.master.entity;

import com.dfs.common.enums.NodeStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "storage_nodes", uniqueConstraints = {
        @UniqueConstraint(columnNames = "node_id")
})
public class StorageNode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "node_id", nullable = false, length = 50)
    private String nodeId;

    @Column(nullable = false, length = 255)
    private String host;

    @Column(nullable = false)
    private int port;

    @Column(nullable = false)
    private long capacity;

    @Column(name = "used_storage", nullable = false)
    private long usedStorage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NodeStatus status = NodeStatus.ACTIVE;

    @Column(name = "last_heartbeat")
    private Instant lastHeartbeat;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StorageNode() {
    }

    public StorageNode(String nodeId, String host, int port, long capacity) {
        this.nodeId = nodeId;
        this.host = host;
        this.port = port;
        this.capacity = capacity;
        this.usedStorage = 0;
    }

    public UUID getId() { return id; }
    public String getNodeId() { return nodeId; }
    public String getHost() { return host; }
    public int getPort() { return port; }
    public long getCapacity() { return capacity; }
    public long getUsedStorage() { return usedStorage; }
    public NodeStatus getStatus() { return status; }
    public Instant getLastHeartbeat() { return lastHeartbeat; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setUsedStorage(long usedStorage) { this.usedStorage = usedStorage; }
    public void setStatus(NodeStatus status) { this.status = status; }
    public void setLastHeartbeat(Instant lastHeartbeat) { this.lastHeartbeat = lastHeartbeat; }
    public void setHost(String host) { this.host = host; }
    public void setPort(int port) { this.port = port; }
    public void setCapacity(long capacity) { this.capacity = capacity; }

    public long getAvailableStorage() {
        return capacity - usedStorage;
    }

    public String getBaseUrl() {
        return "http://" + host + ":" + port;
    }
}
