package com.dfs.master.repository;

import com.dfs.common.enums.FileStatus;
import com.dfs.master.entity.FileMetadata;
import com.dfs.master.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FileMetadataRepository extends JpaRepository<FileMetadata, UUID> {

    Page<FileMetadata> findByOwnerAndStatusNot(User owner, FileStatus status, Pageable pageable);

    Page<FileMetadata> findByOwnerAndStatus(User owner, FileStatus status, Pageable pageable);

    Optional<FileMetadata> findByIdAndOwner(UUID id, User owner);

    @Query("SELECT f FROM FileMetadata f WHERE f.owner = :owner AND f.status <> 'DELETED' " +
           "AND LOWER(f.fileName) LIKE LOWER(CONCAT('%', :query, '%'))")
    Page<FileMetadata> searchByOwnerAndFileName(@Param("owner") User owner,
                                                @Param("query") String query,
                                                Pageable pageable);

    long countByOwnerAndStatusNot(User owner, FileStatus status);

    @Query("SELECT COALESCE(SUM(f.fileSize), 0) FROM FileMetadata f " +
           "WHERE f.owner = :owner AND f.status = 'ACTIVE'")
    long sumFileSizeByOwner(@Param("owner") User owner);

    List<FileMetadata> findByStatus(FileStatus status);
}
