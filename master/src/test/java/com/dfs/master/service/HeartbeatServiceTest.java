package com.dfs.master.service;

import com.dfs.common.enums.NodeStatus;
import com.dfs.master.dto.HeartbeatRequest;
import com.dfs.master.entity.StorageNode;
import com.dfs.master.repository.StorageNodeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HeartbeatServiceTest {

    @Mock
    private StorageNodeRepository storageNodeRepository;

    @InjectMocks
    private HeartbeatService heartbeatService;

    @Test
    @DisplayName("Should update node timestamp and used storage on valid heartbeat")
    void shouldUpdateNodeMetricsOnHeartbeat() {
        StorageNode node = new StorageNode("node-1", "localhost", 9001, 100_000L);
        node.setStatus(NodeStatus.ACTIVE);
        node.setUsedStorage(1000L);
        node.setLastHeartbeat(Instant.now().minusSeconds(10));

        when(storageNodeRepository.findByNodeId("node-1")).thenReturn(Optional.of(node));

        HeartbeatRequest request = new HeartbeatRequest(
                "node-1",
                5000L,
                95000L,
                12
        );

        heartbeatService.processHeartbeat(request);

        ArgumentCaptor<StorageNode> captor = ArgumentCaptor.forClass(StorageNode.class);
        verify(storageNodeRepository).save(captor.capture());

        StorageNode saved = captor.getValue();
        assertThat(saved.getUsedStorage()).isEqualTo(5000L);
        assertThat(saved.getLastHeartbeat()).isAfter(Instant.now().minusSeconds(5));
    }

    @Test
    @DisplayName("Should recover OFFLINE node to ACTIVE on heartbeat receipt")
    void shouldRecoverOfflineNodeToActive() {
        StorageNode node = new StorageNode("node-2", "localhost", 9002, 100_000L);
        node.setStatus(NodeStatus.OFFLINE);
        node.setUsedStorage(0L);

        when(storageNodeRepository.findByNodeId("node-2")).thenReturn(Optional.of(node));

        HeartbeatRequest request = new HeartbeatRequest(
                "node-2",
                1000L,
                99000L,
                5
        );

        heartbeatService.processHeartbeat(request);

        ArgumentCaptor<StorageNode> captor = ArgumentCaptor.forClass(StorageNode.class);
        verify(storageNodeRepository).save(captor.capture());

        StorageNode saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(NodeStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should ignore heartbeat for unknown node gracefully")
    void shouldIgnoreUnknownNode() {
        when(storageNodeRepository.findByNodeId("unknown-node")).thenReturn(Optional.empty());

        HeartbeatRequest request = new HeartbeatRequest(
                "unknown-node",
                100L,
                900L,
                1
        );

        heartbeatService.processHeartbeat(request);

        verify(storageNodeRepository, never()).save(any());
    }
}
