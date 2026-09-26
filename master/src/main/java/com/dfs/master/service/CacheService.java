package com.dfs.master.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Redis-based caching for hot file metadata to reduce PostgreSQL read load.
 */
@Service
public class CacheService {

    private static final Logger log = LoggerFactory.getLogger(CacheService.class);
    private static final String FILE_CACHE_PREFIX = "dfs:file:";
    private static final String NODE_CACHE_PREFIX = "dfs:node:";
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    private final RedisTemplate<String, Object> redisTemplate;

    public CacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void cacheFileMetadata(String fileId, Object metadata) {
        try {
            redisTemplate.opsForValue().set(FILE_CACHE_PREFIX + fileId, metadata, DEFAULT_TTL);
        } catch (Exception e) {
            log.debug("Failed to cache file metadata: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T getCachedFileMetadata(String fileId, Class<T> type) {
        try {
            Object cached = redisTemplate.opsForValue().get(FILE_CACHE_PREFIX + fileId);
            if (cached != null && type.isInstance(cached)) {
                return type.cast(cached);
            }
        } catch (Exception e) {
            log.debug("Failed to read cached file metadata: {}", e.getMessage());
        }
        return null;
    }

    public void evictFileMetadata(String fileId) {
        try {
            redisTemplate.delete(FILE_CACHE_PREFIX + fileId);
        } catch (Exception e) {
            log.debug("Failed to evict cached file metadata: {}", e.getMessage());
        }
    }

    public void cacheNodeStatus(String nodeId, Object status) {
        try {
            redisTemplate.opsForValue().set(NODE_CACHE_PREFIX + nodeId, status, Duration.ofSeconds(30));
        } catch (Exception e) {
            log.debug("Failed to cache node status: {}", e.getMessage());
        }
    }

    public void evictNodeStatus(String nodeId) {
        try {
            redisTemplate.delete(NODE_CACHE_PREFIX + nodeId);
        } catch (Exception e) {
            log.debug("Failed to evict cached node status: {}", e.getMessage());
        }
    }
}
