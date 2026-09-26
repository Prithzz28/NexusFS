package com.dfs.storage.service;

import com.dfs.storage.config.StorageNodeProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class LocalStorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalStorageService.class);
    private final StorageNodeProperties properties;
    private Path storageRootPath;

    public LocalStorageService(StorageNodeProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() throws IOException {
        storageRootPath = Path.of(properties.storageRoot());
        Files.createDirectories(storageRootPath);
        log.info("Storage node [{}] initialized at: {}", properties.nodeId(), storageRootPath);
    }

    public long getUsedStorageBytes() {
        try (var walk = Files.walk(storageRootPath)) {
            return walk.filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try {
                            return Files.size(p);
                        } catch (IOException e) {
                            return 0L;
                        }
                    })
                    .sum();
        } catch (IOException e) {
            log.warn("Failed to calculate used storage", e);
            return 0L;
        }
    }

    public long getAvailableBytes() {
        return properties.capacityBytes() - getUsedStorageBytes();
    }

    public long getChunkCount() {
        try (var walk = Files.walk(storageRootPath)) {
            return walk.filter(Files::isRegularFile).count();
        } catch (IOException e) {
            log.warn("Failed to count chunks", e);
            return 0L;
        }
    }

    public Path getStorageRootPath() {
        return storageRootPath;
    }
}
