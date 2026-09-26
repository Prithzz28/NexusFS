package com.dfs.master.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Redis-based distributed lock service using SETNX pattern.
 * Provides mutual exclusion for concurrent modifications to the same resource.
 */
@Service
public class DistributedLockService {

    private static final Logger log = LoggerFactory.getLogger(DistributedLockService.class);
    private static final String LOCK_PREFIX = "dfs:lock:";
    private static final Duration DEFAULT_LOCK_TTL = Duration.ofSeconds(30);

    private final RedisTemplate<String, Object> redisTemplate;

    public DistributedLockService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Attempts to acquire a distributed lock.
     * Returns true if the lock was acquired, false if it's already held.
     */
    public boolean tryLock(String resourceId) {
        return tryLock(resourceId, DEFAULT_LOCK_TTL);
    }

    public boolean tryLock(String resourceId, Duration ttl) {
        String key = LOCK_PREFIX + resourceId;
        try {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(key, Thread.currentThread().getName(), ttl);
            return Boolean.TRUE.equals(acquired);
        } catch (Exception e) {
            log.warn("Failed to acquire distributed lock for {}: {}", resourceId, e.getMessage());
            return false;
        }
    }

    /**
     * Releases a distributed lock.
     */
    public void unlock(String resourceId) {
        String key = LOCK_PREFIX + resourceId;
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Failed to release distributed lock for {}: {}", resourceId, e.getMessage());
        }
    }

    /**
     * Executes an action under a distributed lock.
     * If the lock cannot be acquired, throws IllegalStateException.
     */
    public <T> T executeWithLock(String resourceId, Supplier<T> action) {
        if (!tryLock(resourceId)) {
            throw new IllegalStateException(
                    "Could not acquire lock for resource: " + resourceId);
        }
        try {
            return action.get();
        } finally {
            unlock(resourceId);
        }
    }

    /**
     * Executes an action under a distributed lock (void version).
     */
    public void executeWithLock(String resourceId, Runnable action) {
        executeWithLock(resourceId, () -> {
            action.run();
            return null;
        });
    }
}
