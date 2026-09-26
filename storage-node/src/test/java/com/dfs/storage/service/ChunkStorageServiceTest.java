package com.dfs.storage.service;

import com.dfs.common.exception.StorageException;
import com.dfs.storage.config.StorageNodeProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChunkStorageServiceTest {

    @TempDir
    Path tempDir;

    private ChunkStorageService chunkStorageService;
    private LocalStorageService localStorageService;
    private StorageNodeProperties properties;

    @BeforeEach
    void setUp() throws IOException {
        properties = new StorageNodeProperties(
                "test-node-1",
                tempDir.toString(),
                1024 * 1024 * 100L, // 100 MB
                "http://localhost:8080",
                5
        );
        localStorageService = new LocalStorageService(properties);
        localStorageService.init();
        chunkStorageService = new ChunkStorageService(properties, localStorageService);
    }

    @Nested
    @DisplayName("Store and Read Chunk Tests")
    class StoreAndReadTests {

        @Test
        @DisplayName("Should successfully store a chunk and compute SHA-256")
        void shouldStoreChunkAndReturnChecksum() throws Exception {
            String chunkId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
            byte[] data = "Hello, Distributed File Storage!".getBytes(StandardCharsets.UTF_8);

            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String expectedChecksum = HexFormat.of().formatHex(md.digest(data));

            String actualChecksum = chunkStorageService.storeChunk(chunkId, new ByteArrayInputStream(data));

            assertThat(actualChecksum).isEqualTo(expectedChecksum);
            assertThat(chunkStorageService.chunkExists(chunkId)).isTrue();
            assertThat(chunkStorageService.getChunkSize(chunkId)).isEqualTo(data.length);

            byte[] retrieved = chunkStorageService.readChunk(chunkId);
            assertThat(retrieved).isEqualTo(data);
        }

        @Test
        @DisplayName("Should stream chunk via getChunkInputStream")
        void shouldStreamChunkData() throws Exception {
            String chunkId = "deadbeef-1234-5678-9abc-def012345678";
            byte[] data = "Streamed chunk test payload".getBytes(StandardCharsets.UTF_8);

            chunkStorageService.storeChunk(chunkId, new ByteArrayInputStream(data));

            try (InputStream in = chunkStorageService.getChunkInputStream(chunkId)) {
                byte[] readBytes = in.readAllBytes();
                assertThat(readBytes).isEqualTo(data);
            }
        }

        @Test
        @DisplayName("Should throw StorageException when reading non-existent chunk")
        void shouldThrowWhenReadingNonExistentChunk() {
            assertThatThrownBy(() -> chunkStorageService.readChunk("non-existent-chunk"))
                    .isInstanceOf(StorageException.class)
                    .hasMessageContaining("Chunk not found");
        }
    }

    @Nested
    @DisplayName("Delete and Verify Tests")
    class DeleteAndVerifyTests {

        @Test
        @DisplayName("Should verify existing chunk checksum accurately")
        void shouldVerifyChunkIntegrity() {
            String chunkId = "feeedcba-0987-6543-210f-edcba9876543";
            byte[] data = "Data to verify".getBytes(StandardCharsets.UTF_8);

            String storedChecksum = chunkStorageService.storeChunk(chunkId, new ByteArrayInputStream(data));
            String verifiedChecksum = chunkStorageService.verifyChunk(chunkId);

            assertThat(verifiedChecksum).isEqualTo(storedChecksum);
        }

        @Test
        @DisplayName("Should return null when verifying non-existent chunk")
        void shouldReturnNullForNonExistentChunkVerify() {
            assertThat(chunkStorageService.verifyChunk("non-existent")).isNull();
        }

        @Test
        @DisplayName("Should delete chunk and clean up empty parent dirs")
        void shouldDeleteChunk() {
            String chunkId = "11223344-5566-7788-9900-aabbccddeeff";
            byte[] data = "Delete me".getBytes(StandardCharsets.UTF_8);

            chunkStorageService.storeChunk(chunkId, new ByteArrayInputStream(data));
            assertThat(chunkStorageService.chunkExists(chunkId)).isTrue();

            boolean deleted = chunkStorageService.deleteChunk(chunkId);
            assertThat(deleted).isTrue();
            assertThat(chunkStorageService.chunkExists(chunkId)).isFalse();
        }
    }
}
