package com.dfs.master.entity;

import com.dfs.common.enums.ChunkReplicaStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chunk_replicas", indexes = {
        @Index(name = "idx_replicas_chunk", columnList = "chunk_id"),
        @Index(name = "idx_replicas_node", columnList = "storage_node_id"),
        @Index(name = "idx_replicas_status", columnList = "status")
})
public class ChunkReplica {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chunk_id", nullable = false)
    private Chunk chunk;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "storage_node_id", nullable = false)
    private StorageNode storageNode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChunkReplicaStatus status = ChunkReplicaStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_verified_at")
    private Instant lastVerifiedAt;

    protected ChunkReplica() {
    }

    public ChunkReplica(Chunk chunk, StorageNode storageNode) {
        this.chunk = chunk;
        this.storageNode = storageNode;
    }

    public UUID getId() { return id; }
    public Chunk getChunk() { return chunk; }
    public StorageNode getStorageNode() { return storageNode; }
    public ChunkReplicaStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastVerifiedAt() { return lastVerifiedAt; }

    public void setStatus(ChunkReplicaStatus status) { this.status = status; }
    public void setLastVerifiedAt(Instant lastVerifiedAt) { this.lastVerifiedAt = lastVerifiedAt; }
}
