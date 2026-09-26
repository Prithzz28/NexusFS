package com.dfs.master.entity;

import com.dfs.common.enums.UploadSessionStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "upload_sessions", indexes = {
        @Index(name = "idx_sessions_user", columnList = "user_id"),
        @Index(name = "idx_sessions_file", columnList = "file_id"),
        @Index(name = "idx_sessions_status", columnList = "status")
})
public class UploadSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false)
    private FileMetadata file;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UploadSessionStatus status = UploadSessionStatus.INITIATED;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected UploadSession() {
    }

    public UploadSession(User user, FileMetadata file, Instant expiresAt) {
        this.user = user;
        this.file = file;
        this.expiresAt = expiresAt;
    }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public FileMetadata getFile() { return file; }
    public UploadSessionStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }

    public void setStatus(UploadSessionStatus status) { this.status = status; }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
