package com.dfs.storage.controller;

import com.dfs.storage.service.ChunkStorageService;
import com.dfs.storage.service.LocalStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ChunkControllerTest {

    @Mock
    private ChunkStorageService chunkStorageService;

    @Mock
    private LocalStorageService localStorageService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ChunkController controller = new ChunkController(chunkStorageService, localStorageService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("PUT /internal/chunks/{id} should store chunk and return 201")
    void shouldStoreChunk() throws Exception {
        String chunkId = "chunk-12345";
        byte[] payload = "Hello Chunk".getBytes(StandardCharsets.UTF_8);

        when(localStorageService.getAvailableBytes()).thenReturn(10_000_000L);
        when(chunkStorageService.storeChunk(eq(chunkId), any(InputStream.class))).thenReturn("sha256-hash");
        when(chunkStorageService.getChunkSize(chunkId)).thenReturn((long) payload.length);

        mockMvc.perform(put("/internal/chunks/{chunkId}", chunkId)
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.chunkId").value(chunkId))
                .andExpect(jsonPath("$.data.checksum").value("sha256-hash"))
                .andExpect(jsonPath("$.data.sizeBytes").value(payload.length));
    }

    @Test
    @DisplayName("PUT /internal/chunks/{id} should return 507 when storage is full")
    void shouldReturnInsufficientStorageWhenFull() throws Exception {
        String chunkId = "chunk-full";
        when(localStorageService.getAvailableBytes()).thenReturn(0L);

        mockMvc.perform(put("/internal/chunks/{chunkId}", chunkId)
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(new byte[]{1, 2, 3}))
                .andExpect(status().isInsufficientStorage())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INSUFFICIENT_STORAGE"));
    }

    @Test
    @DisplayName("GET /internal/chunks/{id} should return raw chunk bytes")
    void shouldGetChunk() throws Exception {
        String chunkId = "chunk-abc";
        byte[] data = "chunk content bytes".getBytes(StandardCharsets.UTF_8);

        when(chunkStorageService.chunkExists(chunkId)).thenReturn(true);
        when(chunkStorageService.readChunk(chunkId)).thenReturn(data);

        mockMvc.perform(get("/internal/chunks/{chunkId}", chunkId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Length", String.valueOf(data.length)))
                .andExpect(content().bytes(data));
    }

    @Test
    @DisplayName("GET /internal/chunks/{id} should return 404 when chunk not found")
    void shouldReturn404WhenChunkNotFound() throws Exception {
        String chunkId = "chunk-missing";
        when(chunkStorageService.chunkExists(chunkId)).thenReturn(false);

        mockMvc.perform(get("/internal/chunks/{chunkId}", chunkId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /internal/chunks/{id} should delete chunk")
    void shouldDeleteChunk() throws Exception {
        String chunkId = "chunk-delete";
        when(chunkStorageService.deleteChunk(chunkId)).thenReturn(true);

        mockMvc.perform(delete("/internal/chunks/{chunkId}", chunkId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.deleted").value(true));

        verify(chunkStorageService).deleteChunk(chunkId);
    }

    @Test
    @DisplayName("HEAD /internal/chunks/{id}/verify should return 200 with checksum header")
    void shouldVerifyChunkHead() throws Exception {
        String chunkId = "chunk-verify";
        when(chunkStorageService.verifyChunk(chunkId)).thenReturn("abc123hash");
        when(chunkStorageService.getChunkSize(chunkId)).thenReturn(1024L);

        mockMvc.perform(head("/internal/chunks/{chunkId}/verify", chunkId))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Chunk-Checksum", "abc123hash"))
                .andExpect(header().string("X-Chunk-Size", "1024"));
    }
}
