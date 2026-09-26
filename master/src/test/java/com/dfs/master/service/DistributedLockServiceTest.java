package com.dfs.master.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DistributedLockServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private DistributedLockService lockService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lockService = new DistributedLockService(redisTemplate);
    }

    @Test
    @DisplayName("Should successfully acquire lock when SETNX returns true")
    void shouldAcquireLockSuccessfully() {
        when(valueOperations.setIfAbsent(eq("dfs:lock:file-123"), anyString(), any(Duration.class)))
                .thenReturn(Boolean.TRUE);

        boolean acquired = lockService.tryLock("file-123");

        assertThat(acquired).isTrue();
    }

    @Test
    @DisplayName("Should fail to acquire lock when already held")
    void shouldFailToAcquireLockWhenHeld() {
        when(valueOperations.setIfAbsent(eq("dfs:lock:file-123"), anyString(), any(Duration.class)))
                .thenReturn(Boolean.FALSE);

        boolean acquired = lockService.tryLock("file-123");

        assertThat(acquired).isFalse();
    }

    @Test
    @DisplayName("Should execute action within lock and release afterwards")
    void shouldExecuteActionWithLock() {
        when(valueOperations.setIfAbsent(eq("dfs:lock:res-1"), anyString(), any(Duration.class)))
                .thenReturn(Boolean.TRUE);

        String result = lockService.executeWithLock("res-1", () -> "success-val");

        assertThat(result).isEqualTo("success-val");
        verify(redisTemplate).delete("dfs:lock:res-1");
    }

    @Test
    @DisplayName("Should throw IllegalStateException when lock cannot be acquired during executeWithLock")
    void shouldThrowWhenExecuteFailsToAcquireLock() {
        when(valueOperations.setIfAbsent(eq("dfs:lock:res-busy"), anyString(), any(Duration.class)))
                .thenReturn(Boolean.FALSE);

        assertThatThrownBy(() -> lockService.executeWithLock("res-busy", () -> "fail"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Could not acquire lock");

        verify(redisTemplate, never()).delete(anyString());
    }
}
