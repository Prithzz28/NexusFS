package com.dfs.master.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chunks", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"file_id", "chunk_index"})
}, indexes = {
        @Index(name = "idx_chunks_file", columnList = "file_id")
})
public class Chunk {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false)
    private FileMetadata file;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(name = "chunk_size", nullable = false)
    private long chunkSize;

    @Column(length = 64)
    private String checksum;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Chunk() {
    }

    public Chunk(FileMetadata file, int chunkIndex, long chunkSize, String checksum) {
        this.file = file;
        this.chunkIndex = chunkIndex;
        this.chunkSize = chunkSize;
        this.checksum = checksum;
    }

    public UUID getId() { return id; }
    public FileMetadata getFile() { return file; }
    public int getChunkIndex() { return chunkIndex; }
    public long getChunkSize() { return chunkSize; }
    public String getChecksum() { return checksum; }
    public Instant getCreatedAt() { return createdAt; }

    public void setChecksum(String checksum) { this.checksum = checksum; }
}
