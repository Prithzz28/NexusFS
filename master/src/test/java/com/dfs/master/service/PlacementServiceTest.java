package com.dfs.master.service;

import com.dfs.common.enums.NodeStatus;
import com.dfs.master.config.DfsProperties;
import com.dfs.master.entity.StorageNode;
import com.dfs.master.exception.InsufficientStorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlacementServiceTest {

    @Mock
    private StorageNodeService storageNodeService;

    private DfsProperties dfsProperties;
    private PlacementService placementService;

    @BeforeEach
    void setUp() {
        dfsProperties = new DfsProperties(
                4194304L, // 4MB
                3,        // replication factor
                5,
                15,
                1073741824L,
                10
        );
        placementService = new PlacementService(storageNodeService, dfsProperties);
    }

    private StorageNode createNode(String nodeId, long totalStorage, long usedStorage) {
        StorageNode node = new StorageNode(nodeId, "localhost", 9000, totalStorage);
        node.setUsedStorage(usedStorage);
        node.setStatus(NodeStatus.ACTIVE);
        return node;
    }

    @Test
    @DisplayName("Should select replicationFactor number of nodes with sufficient space")
    void shouldSelectQualifiedNodes() {
        StorageNode n1 = createNode("node-1", 100_000_000L, 10_000_000L); // 90MB free
        StorageNode n2 = createNode("node-2", 100_000_000L, 20_000_000L); // 80MB free
        StorageNode n3 = createNode("node-3", 100_000_000L, 30_000_000L); // 70MB free
        StorageNode n4 = createNode("node-4", 100_000_000L, 40_000_000L); // 60MB free

        when(storageNodeService.getActiveNodesForPlacement()).thenReturn(List.of(n1, n2, n3, n4));

        List<StorageNode> selected = placementService.selectNodesForChunk(4_194_304L);

        assertThat(selected).hasSize(3);
        assertThat(selected).containsExactly(n1, n2, n3);
    }

    @Test
    @DisplayName("Should throw InsufficientStorageException when fewer nodes than replication factor are active")
    void shouldThrowWhenFewerNodesActive() {
        StorageNode n1 = createNode("node-1", 100_000_000L, 10_000_000L);
        StorageNode n2 = createNode("node-2", 100_000_000L, 10_000_000L);

        when(storageNodeService.getActiveNodesForPlacement()).thenReturn(List.of(n1, n2));

        assertThatThrownBy(() -> placementService.selectNodesForChunk(4_194_304L))
                .isInstanceOf(InsufficientStorageException.class)
                .hasMessageContaining("Need 3 active nodes");
    }

    @Test
    @DisplayName("Should throw InsufficientStorageException when nodes have insufficient space")
    void shouldThrowWhenNodesLackSpace() {
        StorageNode n1 = createNode("node-1", 10_000_000L, 1_000_000L); // 9MB free
        StorageNode n2 = createNode("node-2", 10_000_000L, 9_500_000L); // 500KB free (< 4MB)
        StorageNode n3 = createNode("node-3", 10_000_000L, 9_800_000L); // 200KB free (< 4MB)

        when(storageNodeService.getActiveNodesForPlacement()).thenReturn(List.of(n1, n2, n3));

        assertThatThrownBy(() -> placementService.selectNodesForChunk(4_194_304L))
                .isInstanceOf(InsufficientStorageException.class)
                .hasMessageContaining("Need 3 nodes with 4194304 bytes free");
    }

    @Test
    @DisplayName("Should exclude nodes that already hold a replica during re-replication")
    void shouldExcludeNodesDuringReReplication() {
        StorageNode n1 = createNode("node-1", 100_000_000L, 10_000_000L);
        StorageNode n2 = createNode("node-2", 100_000_000L, 10_000_000L);
        StorageNode n3 = createNode("node-3", 100_000_000L, 10_000_000L);

        when(storageNodeService.getActiveNodesForPlacement()).thenReturn(List.of(n1, n2, n3));

        List<StorageNode> target = placementService.selectNodesForReplication(4_194_304L, List.of("node-1", "node-2"), 1);

        assertThat(target).hasSize(1);
        assertThat(target.get(0).getNodeId()).isEqualTo("node-3");
    }
}
