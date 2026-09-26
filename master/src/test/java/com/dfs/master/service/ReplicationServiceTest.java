package com.dfs.master.service;

import com.dfs.common.enums.ChunkReplicaStatus;
import com.dfs.common.enums.UserRole;
import com.dfs.master.config.DfsProperties;
import com.dfs.master.entity.*;
import com.dfs.master.repository.ChunkReplicaRepository;
import com.dfs.master.repository.ChunkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReplicationServiceTest {

    @Mock
    private ChunkRepository chunkRepository;

    @Mock
    private ChunkReplicaRepository chunkReplicaRepository;

    @Mock
    private StorageNodeClient storageNodeClient;

    @Mock
    private PlacementService placementService;

    private DfsProperties dfsProperties;
    private ReplicationService replicationService;
    private User testUser;

    @BeforeEach
    void setUp() {
        dfsProperties = new DfsProperties(
                4194304L,
                3, // target replication factor = 3
                5,
                15,
                1073741824L,
                10
        );
        replicationService = new ReplicationService(
                chunkRepository,
                chunkReplicaRepository,
                storageNodeClient,
                placementService,
                dfsProperties
        );
        testUser = new User("testuser", "test@dfs.com", "hashedpass", UserRole.USER);
    }

    @Test
    @DisplayName("Should return 0 when no chunks are under-replicated")
    void shouldReturnZeroWhenNoUnderReplicatedChunks() {
        when(chunkReplicaRepository.findUnderReplicatedChunkIds(3)).thenReturn(Collections.emptyList());

        int count = replicationService.replicateUnderReplicatedChunks();

        assertThat(count).isEqualTo(0);
        verifyNoInteractions(storageNodeClient);
    }

    @Test
    @DisplayName("Should detect under-replicated chunk and replicate to target node")
    void shouldReplicateUnderReplicatedChunk() {
        UUID chunkId = UUID.randomUUID();
        List<Object[]> rows = Collections.singletonList(new Object[]{chunkId, 1L}); // has 1 replica, needs 2 more

        when(chunkReplicaRepository.findUnderReplicatedChunkIds(3)).thenReturn(rows);

        FileMetadata file = new FileMetadata(testUser, "test.txt", 1000L, "text/plain", 1, 1000L);
        Chunk chunk = new Chunk(file, 0, 1000L, "test-checksum");
        ReflectionTestUtils.setField(chunk, "id", chunkId);

        when(chunkRepository.findById(chunkId)).thenReturn(Optional.of(chunk));

        StorageNode sourceNode = new StorageNode("node-1", "localhost", 9001, 10_000_000L);
        ChunkReplica sourceReplica = new ChunkReplica(chunk, sourceNode);
        sourceReplica.setStatus(ChunkReplicaStatus.ACTIVE);

        when(chunkReplicaRepository.findByChunkAndStatus(chunk, ChunkReplicaStatus.ACTIVE))
                .thenReturn(List.of(sourceReplica));
        when(chunkReplicaRepository.findByChunk(chunk)).thenReturn(List.of(sourceReplica));

        StorageNode targetNode2 = new StorageNode("node-2", "localhost", 9002, 10_000_000L);
        StorageNode targetNode3 = new StorageNode("node-3", "localhost", 9003, 10_000_000L);

        when(placementService.selectNodesForReplication(eq(1000L), anyList(), eq(2)))
                .thenReturn(List.of(targetNode2, targetNode3));

        byte[] chunkData = "dummy content".getBytes(StandardCharsets.UTF_8);
        when(storageNodeClient.readChunk(sourceNode, chunkId.toString())).thenReturn(chunkData);

        when(storageNodeClient.storeChunk(targetNode2, chunkId.toString(), chunkData))
                .thenReturn("test-checksum");
        when(storageNodeClient.storeChunk(targetNode3, chunkId.toString(), chunkData))
                .thenReturn("test-checksum");

        int replicated = replicationService.replicateUnderReplicatedChunks();

        assertThat(replicated).isEqualTo(1);
        verify(chunkReplicaRepository, times(2)).save(any(ChunkReplica.class));
    }

    @Test
    @DisplayName("Should verify replica and mark CORRUPTED if checksum does not match")
    void shouldMarkReplicaCorruptedOnChecksumMismatch() {
        UUID chunkId = UUID.randomUUID();
        FileMetadata file = new FileMetadata(testUser, "test.txt", 1000L, "text/plain", 1, 1000L);
        Chunk chunk = new Chunk(file, 0, 1000L, "expected-checksum");
        ReflectionTestUtils.setField(chunk, "id", chunkId);

        StorageNode node = new StorageNode("node-1", "localhost", 9001, 10_000_000L);
        ChunkReplica replica = new ChunkReplica(chunk, node);

        when(storageNodeClient.verifyChunk(node, chunkId.toString())).thenReturn("corrupted-checksum");

        boolean verified = replicationService.verifyReplica(replica);

        assertThat(verified).isFalse();
        assertThat(replica.getStatus()).isEqualTo(ChunkReplicaStatus.CORRUPTED);
        verify(chunkReplicaRepository).save(replica);
    }
}
