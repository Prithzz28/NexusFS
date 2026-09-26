package com.dfs.master.repository;

import com.dfs.master.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    Page<AuditLog> findByUserIdOrderByTimestampDesc(UUID userId, Pageable pageable);

    Page<AuditLog> findByOperationOrderByTimestampDesc(String operation, Pageable pageable);
}
