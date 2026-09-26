package com.dfs.storage.service;

import com.dfs.storage.config.StorageNodeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;

/**
 * Auto-registers this storage node with the Master server on startup.
 */
@Service
public class NodeRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(NodeRegistrationService.class);
    private final StorageNodeProperties properties;
    private final RestTemplate restTemplate;

    public NodeRegistrationService(StorageNodeProperties properties, RestTemplate restTemplate) {
        this.properties = properties;
        this.restTemplate = restTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void registerWithMaster() {
        String host = resolveHostname();
        Map<String, Object> registration = Map.of(
                "nodeId", properties.nodeId(),
                "host", host,
                "port", getServerPort(),
                "capacityBytes", properties.capacityBytes()
        );

        String url = properties.masterUrl() + "/api/nodes/register";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(registration, headers);
            restTemplate.postForEntity(url, request, Map.class);
            log.info("Successfully registered with Master at {}", properties.masterUrl());
        } catch (RestClientException e) {
            log.warn("Failed to register with Master at {}: {}. Will retry via heartbeat.",
                    properties.masterUrl(), e.getMessage());
        }
    }

    private String resolveHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "localhost";
        }
    }

    private int getServerPort() {
        // The port from StorageNodeProperties or the default
        // We'll use the configured port from the environment
        try {
            String portStr = System.getenv("SERVER_PORT");
            if (portStr != null) {
                return Integer.parseInt(portStr);
            }
        } catch (NumberFormatException e) {
            // ignore
        }
        return 9001; // default
    }
}
