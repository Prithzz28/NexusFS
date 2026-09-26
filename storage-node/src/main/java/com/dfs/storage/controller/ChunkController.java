package com.dfs.storage.controller;

import com.dfs.common.dto.ApiResponse;
import com.dfs.storage.service.ChunkStorageService;
import com.dfs.storage.service.LocalStorageService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.util.Map;

/**
 * Internal REST endpoints for chunk storage operations.
 * Called by the Master server to store, retrieve, delete, and verify chunks.
 */
@RestController
@RequestMapping("/internal/chunks")
public class ChunkController {

    private final ChunkStorageService chunkStorageService;
    private final LocalStorageService localStorageService;

    public ChunkController(ChunkStorageService chunkStorageService,
                           LocalStorageService localStorageService) {
        this.chunkStorageService = chunkStorageService;
        this.localStorageService = localStorageService;
    }

    /**
     * Stores a chunk on this node. Request body is the raw chunk bytes.
     * Returns the computed SHA-256 checksum.
     */
    @PutMapping(value = "/{chunkId}", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<ApiResponse<Map<String, Object>>> storeChunk(
            @PathVariable String chunkId,
            InputStream data) {

        long availableBytes = localStorageService.getAvailableBytes();
        if (availableBytes <= 0) {
            return ResponseEntity.status(HttpStatus.INSUFFICIENT_STORAGE)
                    .body(ApiResponse.error("INSUFFICIENT_STORAGE", "Node has no available space"));
        }

        String checksum = chunkStorageService.storeChunk(chunkId, data);
        long size = chunkStorageService.getChunkSize(chunkId);

        Map<String, Object> result = Map.of(
                "chunkId", chunkId,
                "checksum", checksum,
                "sizeBytes", size
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(result));
    }

    /**
     * Retrieves raw chunk bytes for download.
     */
    @GetMapping(value = "/{chunkId}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> getChunk(@PathVariable String chunkId) {
        if (!chunkStorageService.chunkExists(chunkId)) {
            return ResponseEntity.notFound().build();
        }

        byte[] data = chunkStorageService.readChunk(chunkId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(data.length))
                .body(data);
    }

    /**
     * Deletes a chunk from this node.
     */
    @DeleteMapping("/{chunkId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteChunk(
            @PathVariable String chunkId) {
        boolean deleted = chunkStorageService.deleteChunk(chunkId);

        Map<String, Object> result = Map.of(
                "chunkId", chunkId,
                "deleted", deleted
        );

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * Verifies chunk integrity. Returns the checksum if the chunk exists.
     */
    @RequestMapping(value = "/{chunkId}/verify", method = RequestMethod.HEAD)
    public ResponseEntity<Void> verifyChunk(@PathVariable String chunkId) {
        String checksum = chunkStorageService.verifyChunk(chunkId);
        if (checksum == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .header("X-Chunk-Checksum", checksum)
                .header("X-Chunk-Size", String.valueOf(chunkStorageService.getChunkSize(chunkId)))
                .build();
    }

    /**
     * GET variant of verify for easier testing.
     */
    @GetMapping("/{chunkId}/verify")
    public ResponseEntity<ApiResponse<Map<String, Object>>> verifyChunkGet(
            @PathVariable String chunkId) {
        String checksum = chunkStorageService.verifyChunk(chunkId);
        if (checksum == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> result = Map.of(
                "chunkId", chunkId,
                "checksum", checksum,
                "sizeBytes", chunkStorageService.getChunkSize(chunkId),
                "exists", true
        );

        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
