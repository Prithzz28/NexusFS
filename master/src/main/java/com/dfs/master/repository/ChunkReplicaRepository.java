package com.dfs.master.repository;

import com.dfs.common.enums.ChunkReplicaStatus;
import com.dfs.master.entity.Chunk;
import com.dfs.master.entity.ChunkReplica;
import com.dfs.master.entity.StorageNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChunkReplicaRepository extends JpaRepository<ChunkReplica, UUID> {

    List<ChunkReplica> findByChunk(Chunk chunk);

    List<ChunkReplica> findByChunkAndStatus(Chunk chunk, ChunkReplicaStatus status);

    List<ChunkReplica> findByStorageNode(StorageNode storageNode);

    List<ChunkReplica> findByStorageNodeAndStatus(StorageNode storageNode, ChunkReplicaStatus status);

    long countByChunkAndStatus(Chunk chunk, ChunkReplicaStatus status);

    /**
     * Finds chunks that have fewer than the desired number of ACTIVE replicas.
     * Used by the self-healing scanner to detect under-replicated chunks.
     */
    @Query("SELECT cr.chunk.id, COUNT(cr) as cnt FROM ChunkReplica cr " +
           "WHERE cr.status = 'ACTIVE' " +
           "GROUP BY cr.chunk.id " +
           "HAVING COUNT(cr) < :targetReplicationFactor")
    List<Object[]> findUnderReplicatedChunkIds(@Param("targetReplicationFactor") long targetReplicationFactor);

    void deleteAllByChunk(Chunk chunk);
}
