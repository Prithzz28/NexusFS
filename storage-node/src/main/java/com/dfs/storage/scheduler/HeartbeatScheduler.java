package com.dfs.storage.scheduler;

import com.dfs.storage.config.StorageNodeProperties;
import com.dfs.storage.service.LocalStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Periodically sends heartbeat signals to the Master server.
 * Reports node health metrics: used storage, available storage, chunks stored.
 */
@Component
public class HeartbeatScheduler {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatScheduler.class);

    private final StorageNodeProperties properties;
    private final LocalStorageService storageService;
    private final RestTemplate restTemplate;

    public HeartbeatScheduler(StorageNodeProperties properties,
                              LocalStorageService storageService,
                              RestTemplate restTemplate) {
        this.properties = properties;
        this.storageService = storageService;
        this.restTemplate = restTemplate;
    }

    @Scheduled(fixedDelayString = "${storage.heartbeat-interval-seconds:5}000",
               initialDelay = 10000)
    public void sendHeartbeat() {
        long usedStorage = storageService.getUsedStorageBytes();
        long availableStorage = storageService.getAvailableBytes();
        long chunkCount = storageService.getChunkCount();

        Map<String, Object> heartbeat = Map.of(
                "nodeId", properties.nodeId(),
                "usedStorageBytes", usedStorage,
                "availableStorageBytes", availableStorage,
                "chunksStored", chunkCount
        );

        String url = properties.masterUrl() + "/internal/nodes/heartbeat";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(heartbeat, headers);
            restTemplate.postForEntity(url, request, Map.class);
            log.trace("Heartbeat sent: node={} used={}B available={}B chunks={}",
                    properties.nodeId(), usedStorage, availableStorage, chunkCount);
        } catch (RestClientException e) {
            log.warn("Failed to send heartbeat to {}: {}", url, e.getMessage());
        }
    }
}
