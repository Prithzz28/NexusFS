package com.dfs.master.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_user", columnList = "user_id"),
        @Index(name = "idx_audit_timestamp", columnList = "timestamp")
})
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false, length = 50)
    private String operation;

    @Column(name = "resource_id", length = 100)
    private String resourceId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant timestamp;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(length = 500)
    private String details;

    protected AuditLog() {
    }

    public AuditLog(UUID userId, String operation, String resourceId,
                    String ipAddress, String status, String details) {
        this.userId = userId;
        this.operation = operation;
        this.resourceId = resourceId;
        this.ipAddress = ipAddress;
        this.status = status;
        this.details = details;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getOperation() { return operation; }
    public String getResourceId() { return resourceId; }
    public Instant getTimestamp() { return timestamp; }
    public String getIpAddress() { return ipAddress; }
    public String getStatus() { return status; }
    public String getDetails() { return details; }
}
