package com.dfs.master.service;

import com.dfs.master.entity.AuditLog;
import com.dfs.master.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Records audit trail entries for file operations and system events.
 * Uses REQUIRES_NEW propagation so audit logs persist even if the outer transaction rolls back.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logOperation(UUID userId, String operation, String resourceId,
                             String ipAddress, String status, String details) {
        AuditLog entry = new AuditLog(userId, operation, resourceId, ipAddress, status, details);
        auditLogRepository.save(entry);
        log.debug("Audit: user={} op={} resource={} status={}", userId, operation, resourceId, status);
    }
}
