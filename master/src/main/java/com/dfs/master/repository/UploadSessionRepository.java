package com.dfs.master.repository;

import com.dfs.common.enums.UploadSessionStatus;
import com.dfs.master.entity.UploadSession;
import com.dfs.master.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface UploadSessionRepository extends JpaRepository<UploadSession, UUID> {

    List<UploadSession> findByUserAndStatus(User user, UploadSessionStatus status);

    @Modifying
    @Query("UPDATE UploadSession s SET s.status = 'EXPIRED' " +
           "WHERE s.status IN ('INITIATED', 'IN_PROGRESS') AND s.expiresAt < :now")
    int expireStaleUploadSessions(@Param("now") Instant now);
}
