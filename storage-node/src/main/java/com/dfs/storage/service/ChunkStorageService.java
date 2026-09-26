package com.dfs.storage.service;

import com.dfs.common.constants.StorageConstants;
import com.dfs.common.exception.StorageException;
import com.dfs.storage.config.StorageNodeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Manages chunk persistence on local disk with path-hashed directory structure.
 * Chunks are stored as: {storageRoot}/{ab}/{cd}/{chunkId}
 * where ab and cd are the first 4 hex chars of the chunk ID.
 */
@Service
public class ChunkStorageService {

    private static final Logger log = LoggerFactory.getLogger(ChunkStorageService.class);
    private final StorageNodeProperties properties;
    private final LocalStorageService localStorageService;

    public ChunkStorageService(StorageNodeProperties properties,
                                LocalStorageService localStorageService) {
        this.properties = properties;
        this.localStorageService = localStorageService;
    }

    /**
     * Stores a chunk on disk using path-hashed directory structure.
     * Returns the SHA-256 checksum of the stored data.
     */
    public String storeChunk(String chunkId, InputStream data) {
        Path chunkPath = resolveChunkPath(chunkId);
        try {
            Files.createDirectories(chunkPath.getParent());

            // Write to temp file first, compute checksum, then atomically move
            Path tempFile = chunkPath.resolveSibling(chunkId + ".tmp");
            MessageDigest digest = MessageDigest.getInstance(StorageConstants.CHECKSUM_ALGORITHM);

            try (InputStream in = data) {
                Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }

            // Compute checksum
            String checksum;
            try (InputStream fileIn = Files.newInputStream(tempFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = fileIn.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
                checksum = HexFormat.of().formatHex(digest.digest());
            }

            // Atomic move
            Files.move(tempFile, chunkPath, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);

            long size = Files.size(chunkPath);
            log.debug("Stored chunk {} ({} bytes, checksum={})", chunkId, size, checksum);
            return checksum;

        } catch (IOException | NoSuchAlgorithmException e) {
            throw new StorageException("Failed to store chunk: " + chunkId, e);
        }
    }

    /**
     * Returns a byte array of the chunk data. For streaming, use getChunkInputStream instead.
     */
    public byte[] readChunk(String chunkId) {
        Path chunkPath = resolveChunkPath(chunkId);
        if (!Files.exists(chunkPath)) {
            throw new StorageException("Chunk not found: " + chunkId);
        }
        try {
            return Files.readAllBytes(chunkPath);
        } catch (IOException e) {
            throw new StorageException("Failed to read chunk: " + chunkId, e);
        }
    }

    /**
     * Returns an InputStream for streaming chunk data.
     */
    public InputStream getChunkInputStream(String chunkId) {
        Path chunkPath = resolveChunkPath(chunkId);
        if (!Files.exists(chunkPath)) {
            throw new StorageException("Chunk not found: " + chunkId);
        }
        try {
            return Files.newInputStream(chunkPath);
        } catch (IOException e) {
            throw new StorageException("Failed to open chunk stream: " + chunkId, e);
        }
    }

    /**
     * Deletes a chunk from disk.
     */
    public boolean deleteChunk(String chunkId) {
        Path chunkPath = resolveChunkPath(chunkId);
        try {
            boolean deleted = Files.deleteIfExists(chunkPath);
            if (deleted) {
                log.debug("Deleted chunk: {}", chunkId);
                // Clean up empty parent directories
                cleanEmptyParents(chunkPath.getParent());
            }
            return deleted;
        } catch (IOException e) {
            throw new StorageException("Failed to delete chunk: " + chunkId, e);
        }
    }

    /**
     * Verifies chunk exists and returns its SHA-256 checksum.
     */
    public String verifyChunk(String chunkId) {
        Path chunkPath = resolveChunkPath(chunkId);
        if (!Files.exists(chunkPath)) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance(StorageConstants.CHECKSUM_ALGORITHM);
            try (InputStream in = Files.newInputStream(chunkPath)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new StorageException("Failed to verify chunk: " + chunkId, e);
        }
    }

    /**
     * Checks if a chunk exists on this node.
     */
    public boolean chunkExists(String chunkId) {
        return Files.exists(resolveChunkPath(chunkId));
    }

    /**
     * Returns the size of a stored chunk in bytes.
     */
    public long getChunkSize(String chunkId) {
        Path chunkPath = resolveChunkPath(chunkId);
        try {
            return Files.size(chunkPath);
        } catch (IOException e) {
            throw new StorageException("Failed to get chunk size: " + chunkId, e);
        }
    }

    // ── Path helpers ────────────────────────────────────────

    /**
     * Path-hashed chunk storage: /data/chunks/{ab}/{cd}/{chunkId}
     * Uses the first 4 hex chars of the chunk ID for directory sharding.
     */
    private Path resolveChunkPath(String chunkId) {
        String sanitizedId = chunkId.replace("-", "");
        String dir1 = sanitizedId.substring(0, Math.min(2, sanitizedId.length()));
        String dir2 = sanitizedId.substring(Math.min(2, sanitizedId.length()),
                Math.min(4, sanitizedId.length()));
        return localStorageService.getStorageRootPath()
                .resolve(dir1)
                .resolve(dir2)
                .resolve(chunkId);
    }

    private void cleanEmptyParents(Path dir) {
        try {
            Path root = localStorageService.getStorageRootPath();
            while (dir != null && !dir.equals(root) && Files.isDirectory(dir)) {
                try (var entries = Files.list(dir)) {
                    if (entries.findAny().isEmpty()) {
                        Files.delete(dir);
                        dir = dir.getParent();
                    } else {
                        break;
                    }
                }
            }
        } catch (IOException e) {
            log.debug("Could not clean empty parent directories", e);
        }
    }
}
