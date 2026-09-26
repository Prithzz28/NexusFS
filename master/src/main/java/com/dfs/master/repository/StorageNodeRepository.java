package com.dfs.master.repository;

import com.dfs.common.enums.NodeStatus;
import com.dfs.master.entity.StorageNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StorageNodeRepository extends JpaRepository<StorageNode, UUID> {

    Optional<StorageNode> findByNodeId(String nodeId);

    List<StorageNode> findByStatus(NodeStatus status);

    /**
     * Returns active nodes ordered by available capacity descending.
     * Used by the placement algorithm to choose nodes with the most free space.
     */
    @Query("SELECT n FROM StorageNode n WHERE n.status = 'ACTIVE' " +
           "ORDER BY (n.capacity - n.usedStorage) DESC")
    List<StorageNode> findActiveNodesOrderByAvailableCapacityDesc();

    /**
     * Finds nodes whose last heartbeat is older than the given threshold.
     * Used for failure detection.
     */
    @Query("SELECT n FROM StorageNode n WHERE n.status = :status " +
           "AND (n.lastHeartbeat IS NULL OR n.lastHeartbeat < :threshold)")
    List<StorageNode> findStaleNodes(@Param("status") NodeStatus status,
                                     @Param("threshold") Instant threshold);

    long countByStatus(NodeStatus status);

    boolean existsByNodeId(String nodeId);
}
