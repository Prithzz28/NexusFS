package com.dfs.master.repository;

import com.dfs.master.entity.Chunk;
import com.dfs.master.entity.FileMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChunkRepository extends JpaRepository<Chunk, UUID> {

    List<Chunk> findByFileOrderByChunkIndexAsc(FileMetadata file);

    Optional<Chunk> findByFileAndChunkIndex(FileMetadata file, int chunkIndex);

    long countByFile(FileMetadata file);

    void deleteAllByFile(FileMetadata file);
}
