package com.dfs.master.entity;

import com.dfs.common.enums.FileStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "files", indexes = {
        @Index(name = "idx_files_owner", columnList = "owner_id"),
        @Index(name = "idx_files_status", columnList = "status")
})
public class FileMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "total_chunks", nullable = false)
    private int totalChunks;

    @Column(name = "chunk_size", nullable = false)
    private long chunkSize;

    @Column(length = 64)
    private String checksum;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FileStatus status = FileStatus.UPLOADING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FileMetadata() {
    }

    public FileMetadata(User owner, String fileName, long fileSize, String contentType,
                        int totalChunks, long chunkSize) {
        this.owner = owner;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.contentType = contentType;
        this.totalChunks = totalChunks;
        this.chunkSize = chunkSize;
    }

    public UUID getId() { return id; }
    public User getOwner() { return owner; }
    public String getFileName() { return fileName; }
    public long getFileSize() { return fileSize; }
    public String getContentType() { return contentType; }
    public int getTotalChunks() { return totalChunks; }
    public long getChunkSize() { return chunkSize; }
    public String getChecksum() { return checksum; }
    public FileStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setChecksum(String checksum) { this.checksum = checksum; }
    public void setStatus(FileStatus status) { this.status = status; }
    public void setFileName(String fileName) { this.fileName = fileName; }
}
