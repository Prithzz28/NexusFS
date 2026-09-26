package com.dfs.master.service;

import com.dfs.master.entity.StorageNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * HTTP client for communicating with storage nodes.
 * Handles chunk transfer operations (store, read, delete, verify).
 */
@Service
public class StorageNodeClient {

    private static final Logger log = LoggerFactory.getLogger(StorageNodeClient.class);
    private final RestTemplate restTemplate;

    public StorageNodeClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Sends chunk data to a storage node.
     * Returns the checksum returned by the storage node, or null on failure.
     */
    public String storeChunk(StorageNode node, String chunkId, byte[] data) {
        String url = node.getBaseUrl() + "/internal/chunks/" + chunkId;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            HttpEntity<byte[]> request = new HttpEntity<>(data, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.PUT, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> body = (Map<String, Object>) response.getBody().get("data");
                if (body != null) {
                    return (String) body.get("checksum");
                }
            }
            log.warn("Unexpected response from {} for chunk {}: {}", url, chunkId, response.getStatusCode());
            return null;
        } catch (RestClientException e) {
            log.error("Failed to store chunk {} on node {}: {}", chunkId, node.getNodeId(), e.getMessage());
            return null;
        }
    }

    /**
     * Retrieves chunk data from a storage node.
     */
    public byte[] readChunk(StorageNode node, String chunkId) {
        String url = node.getBaseUrl() + "/internal/chunks/" + chunkId;
        try {
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    url, HttpMethod.GET, null, byte[].class);
            return response.getBody();
        } catch (RestClientException e) {
            log.error("Failed to read chunk {} from node {}: {}", chunkId, node.getNodeId(), e.getMessage());
            return null;
        }
    }

    /**
     * Deletes a chunk from a storage node.
     */
    public boolean deleteChunk(StorageNode node, String chunkId) {
        String url = node.getBaseUrl() + "/internal/chunks/" + chunkId;
        try {
            restTemplate.delete(url);
            return true;
        } catch (RestClientException e) {
            log.error("Failed to delete chunk {} on node {}: {}", chunkId, node.getNodeId(), e.getMessage());
            return false;
        }
    }

    /**
     * Verifies chunk integrity on a storage node.
     * Returns the checksum if the chunk exists, null otherwise.
     */
    public String verifyChunk(StorageNode node, String chunkId) {
        String url = node.getBaseUrl() + "/internal/chunks/" + chunkId + "/verify";
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> body = (Map<String, Object>) response.getBody().get("data");
                if (body != null) {
                    return (String) body.get("checksum");
                }
            }
            return null;
        } catch (RestClientException e) {
            log.warn("Failed to verify chunk {} on node {}: {}", chunkId, node.getNodeId(), e.getMessage());
            return null;
        }
    }

    /**
     * Checks if a storage node is reachable via its health endpoint.
     */
    public boolean isNodeHealthy(StorageNode node) {
        String url = node.getBaseUrl() + "/internal/health";
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (RestClientException e) {
            log.debug("Node {} is not reachable: {}", node.getNodeId(), e.getMessage());
            return false;
        }
    }
}
